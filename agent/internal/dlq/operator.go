package dlq

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
	"github.com/segmentio/kafka-go"
)

const DefaultMaxPayloadBytes = 1 << 20

var destinations = map[string]string{
	"check.commands.v1.dlq":   "check.commands.v1",
	"check.results.v1.dlq":    "check.results.v1",
	"agent.heartbeats.v1.dlq": "agent.heartbeats.v1",
}

type Coordinate struct {
	Topic     string
	Partition int
	Offset    int64
}

type Inspection struct {
	Topic             string   `json:"topic"`
	Partition         int      `json:"partition"`
	Offset            int64    `json:"offset"`
	DestinationTopic  string   `json:"destinationTopic"`
	KeySHA256         string   `json:"keySha256"`
	PayloadSHA256     string   `json:"payloadSha256"`
	PayloadBytes      int      `json:"payloadBytes"`
	HeaderNames       []string `json:"headerNames"`
	OriginalTopic     string   `json:"originalTopic,omitempty"`
	OriginalPartition string   `json:"originalPartition,omitempty"`
	OriginalOffset    string   `json:"originalOffset,omitempty"`
	Error             string   `json:"error,omitempty"`
	Payload           string   `json:"payload,omitempty"`
}

type RedriveRequest struct {
	Coordinate      Coordinate
	ExpectedSHA256  string
	Operator        string
	Reason          string
	Execute         bool
	MaxPayloadBytes int
}

type Operator struct {
	brokers []string
}

func NewOperator(brokers []string) *Operator {
	return &Operator{brokers: brokers}
}

func (o *Operator) Inspect(ctx context.Context, coordinate Coordinate, showPayload bool, maxBytes int) (Inspection, error) {
	message, err := o.readExact(ctx, coordinate, maxBytes)
	if err != nil {
		return Inspection{}, err
	}
	inspection, err := inspect(message, coordinate)
	if err != nil {
		return Inspection{}, err
	}
	if showPayload {
		inspection.Payload = string(message.Value)
	}
	return inspection, nil
}

func (o *Operator) Redrive(ctx context.Context, request RedriveRequest) (Inspection, error) {
	message, err := o.readExact(ctx, request.Coordinate, request.MaxPayloadBytes)
	if err != nil {
		return Inspection{}, err
	}
	inspection, err := inspect(message, request.Coordinate)
	if err != nil {
		return Inspection{}, err
	}
	if !strings.EqualFold(inspection.PayloadSHA256, strings.TrimSpace(request.ExpectedSHA256)) {
		return Inspection{}, errors.New("payload SHA-256 does not match the requested dead letter")
	}
	if strings.TrimSpace(request.Operator) == "" || strings.TrimSpace(request.Reason) == "" {
		return Inspection{}, errors.New("operator and reason are required for redrive")
	}
	if err := validatePayload(request.Coordinate.Topic, message.Value); err != nil {
		return Inspection{}, err
	}
	if !request.Execute {
		return inspection, nil
	}

	destination := destinations[request.Coordinate.Topic]
	headers := sanitizedHeaders(message.Headers)
	headers = append(headers,
		kafka.Header{Key: "pulseops-redrive-id", Value: []byte(randomID())},
		kafka.Header{Key: "pulseops-redriven-from-topic", Value: []byte(request.Coordinate.Topic)},
		kafka.Header{Key: "pulseops-redriven-from-partition", Value: []byte(strconv.Itoa(request.Coordinate.Partition))},
		kafka.Header{Key: "pulseops-redriven-from-offset", Value: []byte(strconv.FormatInt(request.Coordinate.Offset, 10))},
		kafka.Header{Key: "pulseops-redrive-operator", Value: []byte(strings.TrimSpace(request.Operator))},
		kafka.Header{Key: "pulseops-redrive-reason", Value: []byte(strings.TrimSpace(request.Reason))},
		kafka.Header{Key: "pulseops-redriven-at", Value: []byte(time.Now().UTC().Format(time.RFC3339Nano))},
	)
	writer := &kafka.Writer{
		Addr:         kafka.TCP(o.brokers...),
		Topic:        destination,
		RequiredAcks: kafka.RequireAll,
	}
	defer writer.Close()
	if err := writer.WriteMessages(ctx, kafka.Message{
		Key:     message.Key,
		Value:   message.Value,
		Headers: headers,
	}); err != nil {
		return Inspection{}, fmt.Errorf("publish redriven record: %w", err)
	}
	return inspection, nil
}

func (o *Operator) readExact(ctx context.Context, coordinate Coordinate, maxBytes int) (kafka.Message, error) {
	if _, ok := destinations[coordinate.Topic]; !ok {
		return kafka.Message{}, fmt.Errorf("topic %q is not an allowlisted PulseOps DLQ", coordinate.Topic)
	}
	if coordinate.Partition < 0 || coordinate.Offset < 0 {
		return kafka.Message{}, errors.New("partition and offset must be non-negative")
	}
	if maxBytes <= 0 {
		maxBytes = DefaultMaxPayloadBytes
	}
	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:   o.brokers,
		Topic:     coordinate.Topic,
		Partition: coordinate.Partition,
		MinBytes:  1,
		MaxBytes:  maxBytes + 1,
	})
	defer reader.Close()
	if err := reader.SetOffset(coordinate.Offset); err != nil {
		return kafka.Message{}, fmt.Errorf("seek dead letter: %w", err)
	}
	message, err := reader.FetchMessage(ctx)
	if err != nil {
		return kafka.Message{}, fmt.Errorf("read dead letter: %w", err)
	}
	if message.Offset != coordinate.Offset {
		return kafka.Message{}, fmt.Errorf("offset %d does not exist in partition %d", coordinate.Offset, coordinate.Partition)
	}
	if len(message.Value) > maxBytes {
		return kafka.Message{}, fmt.Errorf("payload exceeds the %d byte safety limit", maxBytes)
	}
	return message, nil
}

func inspect(message kafka.Message, coordinate Coordinate) (Inspection, error) {
	destination, ok := destinations[coordinate.Topic]
	if !ok {
		return Inspection{}, fmt.Errorf("topic %q is not an allowlisted PulseOps DLQ", coordinate.Topic)
	}
	inspection := Inspection{
		Topic:            coordinate.Topic,
		Partition:        coordinate.Partition,
		Offset:           coordinate.Offset,
		DestinationTopic: destination,
		KeySHA256:        hash(message.Key),
		PayloadSHA256:    hash(message.Value),
		PayloadBytes:     len(message.Value),
		HeaderNames:      make([]string, 0, len(message.Headers)),
	}
	for _, header := range message.Headers {
		inspection.HeaderNames = append(inspection.HeaderNames, header.Key)
		switch header.Key {
		case "pulseops-original-topic", "kafka_dlt-original-topic":
			inspection.OriginalTopic = printableHeader(header.Value)
		case "pulseops-original-partition", "kafka_dlt-original-partition":
			inspection.OriginalPartition = printableHeader(header.Value)
		case "pulseops-original-offset", "kafka_dlt-original-offset":
			inspection.OriginalOffset = printableHeader(header.Value)
		case "pulseops-error", "kafka_dlt-exception-message":
			inspection.Error = bounded(string(header.Value), 512)
		}
	}
	return inspection, nil
}

func validatePayload(topic string, payload []byte) error {
	switch topic {
	case "check.commands.v1.dlq":
		var command model.CheckCommand
		if err := json.Unmarshal(payload, &command); err != nil {
			return fmt.Errorf("invalid command JSON: %w", err)
		}
		if command.ExecutionID == "" || command.MonitorID == "" || command.Type == "" || command.Location == "" {
			return errors.New("command is missing required fields")
		}
	case "check.results.v1.dlq":
		var result model.CheckResult
		if err := json.Unmarshal(payload, &result); err != nil {
			return fmt.Errorf("invalid result JSON: %w", err)
		}
		if result.ExecutionID == "" || result.MonitorID == "" || result.AgentID == "" || result.Status == "" || result.CheckedAt.IsZero() {
			return errors.New("result is missing required fields")
		}
	case "agent.heartbeats.v1.dlq":
		var heartbeat model.AgentHeartbeat
		if err := json.Unmarshal(payload, &heartbeat); err != nil {
			return fmt.Errorf("invalid heartbeat JSON: %w", err)
		}
		if heartbeat.AgentID == "" || heartbeat.Location == "" || heartbeat.SentAt.IsZero() {
			return errors.New("heartbeat is missing required fields")
		}
	default:
		return errors.New("unsupported dead-letter topic")
	}
	return nil
}

func sanitizedHeaders(headers []kafka.Header) []kafka.Header {
	result := make([]kafka.Header, 0, len(headers))
	for _, header := range headers {
		key := strings.ToLower(header.Key)
		if strings.HasPrefix(key, "kafka_dlt-") ||
			strings.HasPrefix(key, "pulseops-original-") ||
			strings.HasPrefix(key, "pulseops-redrive-") ||
			key == "pulseops-error" {
			continue
		}
		result = append(result, header)
	}
	return result
}

func hash(value []byte) string {
	sum := sha256.Sum256(value)
	return hex.EncodeToString(sum[:])
}

func randomID() string {
	value := make([]byte, 16)
	if _, err := rand.Read(value); err != nil {
		panic("crypto/rand unavailable: " + err.Error())
	}
	return hex.EncodeToString(value)
}

func printableHeader(value []byte) string {
	for _, character := range value {
		if character < 32 || character > 126 {
			return "0x" + hex.EncodeToString(value)
		}
	}
	return string(value)
}

func bounded(value string, limit int) string {
	if len(value) <= limit {
		return value
	}
	return value[:limit]
}
