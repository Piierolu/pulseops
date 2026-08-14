package messaging

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/pulseops/pulseops/agent/internal/checks"
	"github.com/pulseops/pulseops/agent/internal/model"
	"github.com/pulseops/pulseops/agent/internal/telemetry"
	"github.com/segmentio/kafka-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

type Config struct {
	Brokers             []string
	CommandsTopic       string
	CommandsDLQTopic    string
	ResultsTopic        string
	HeartbeatsTopic     string
	AgentID             string
	Location            string
	Version             string
	RetryMaxAttempts    int
	RetryInitialBackoff time.Duration
	RetryMaxBackoff     time.Duration
}

type Worker struct {
	reader          *kafka.Reader
	writer          *kafka.Writer
	dlqWriter       *kafka.Writer
	heartbeatWriter *kafka.Writer
	runner          *checks.Runner
	metrics         *telemetry.AgentMetrics
	config          Config
	logger          *slog.Logger
}

type permanentCommandError struct {
	err error
}

func (e *permanentCommandError) Error() string {
	return e.err.Error()
}

func (e *permanentCommandError) Unwrap() error {
	return e.err
}

func NewWorker(
	config Config,
	runner *checks.Runner,
	metrics *telemetry.AgentMetrics,
	logger *slog.Logger,
) *Worker {
	return &Worker{
		reader: kafka.NewReader(kafka.ReaderConfig{
			Brokers:        config.Brokers,
			Topic:          config.CommandsTopic,
			GroupID:        "pulseops-agents-" + config.Location,
			MinBytes:       1,
			MaxBytes:       10e6,
			CommitInterval: 0,
		}),
		writer:          newWriter(config.Brokers, config.ResultsTopic, 10*time.Millisecond),
		dlqWriter:       newWriter(config.Brokers, config.CommandsDLQTopic, 0),
		heartbeatWriter: newWriter(config.Brokers, config.HeartbeatsTopic, 0),
		runner:          runner,
		metrics:         metrics,
		config:          config,
		logger:          logger,
	}
}

func newWriter(brokers []string, topic string, batchTimeout time.Duration) *kafka.Writer {
	return &kafka.Writer{
		Addr:         kafka.TCP(brokers...),
		Topic:        topic,
		RequiredAcks: kafka.RequireAll,
		BatchTimeout: batchTimeout,
	}
}

func (w *Worker) Run(ctx context.Context) error {
	runContext, cancel := context.WithCancel(ctx)
	heartbeatDone := make(chan struct{})
	go func() {
		defer close(heartbeatDone)
		w.runHeartbeats(runContext)
	}()
	defer func() {
		cancel()
		<-heartbeatDone
		w.close()
	}()
	w.logger.Info("agent started", "agent_id", w.config.AgentID, "location", w.config.Location)

	for {
		message, err := w.reader.FetchMessage(runContext)
		if err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("fetch command: %w", err)
		}

		if err := w.handleMessage(runContext, message); err != nil {
			if ctx.Err() != nil {
				return nil
			}
			return err
		}
	}
}

func (w *Worker) handleMessage(ctx context.Context, message kafka.Message) error {
	messageContext := otel.GetTextMapPropagator().Extract(ctx, kafkaHeaderCarrier{headers: &message.Headers})
	messageContext, span := otel.Tracer("pulseops-agent/messaging").Start(
		messageContext,
		"kafka consume "+message.Topic,
		trace.WithSpanKind(trace.SpanKindConsumer),
		trace.WithAttributes(
			attribute.String("messaging.system", "kafka"),
			attribute.String("messaging.destination.name", message.Topic),
			attribute.Int("messaging.kafka.partition", message.Partition),
			attribute.Int64("messaging.kafka.offset", message.Offset),
		),
	)
	defer span.End()

	var processingError error
	for attempt := 1; attempt <= w.config.RetryMaxAttempts; attempt++ {
		processingError = w.process(messageContext, message)
		if processingError == nil {
			if err := w.commit(messageContext, message); err != nil {
				span.RecordError(err)
				span.SetStatus(codes.Error, err.Error())
				return err
			}
			return nil
		}

		var permanent *permanentCommandError
		if errors.As(processingError, &permanent) {
			break
		}
		if attempt < w.config.RetryMaxAttempts {
			w.metrics.Retry(message.Topic)
			w.logger.Warn("retrying command", "attempt", attempt+1, "error", processingError)
			if err := waitForRetry(
				messageContext,
				w.config.RetryInitialBackoff,
				w.config.RetryMaxBackoff,
				attempt,
			); err != nil {
				return err
			}
		}
	}

	span.RecordError(processingError)
	if err := w.publishDeadLetter(messageContext, message, processingError); err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return fmt.Errorf("publish command to dead-letter topic: %w", err)
	}
	span.AddEvent("command sent to dead-letter topic")
	w.metrics.Command("dlq", "")
	if err := w.commit(messageContext, message); err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return err
	}
	return nil
}

func (w *Worker) process(ctx context.Context, message kafka.Message) error {
	var command model.CheckCommand
	if err := json.Unmarshal(message.Value, &command); err != nil {
		return &permanentCommandError{err: fmt.Errorf("decode command: %w", err)}
	}
	if command.ExecutionID == "" || command.MonitorID == "" || command.Type == "" || command.Location == "" {
		return &permanentCommandError{err: errors.New("command is missing required fields")}
	}
	if command.Location != w.config.Location {
		w.metrics.Command("skipped", command.Type)
		return nil
	}

	startedAt := time.Now()
	w.metrics.InflightInc()
	result := w.runner.Execute(ctx, command, w.config.AgentID)
	w.metrics.InflightDec()
	w.metrics.ObserveCheck(command.Type, result.Status, time.Since(startedAt).Seconds())

	payload, err := json.Marshal(result)
	if err != nil {
		return fmt.Errorf("encode result: %w", err)
	}
	publishContext, span := otel.Tracer("pulseops-agent/messaging").Start(
		ctx,
		"kafka publish "+w.config.ResultsTopic,
		trace.WithSpanKind(trace.SpanKindProducer),
		trace.WithAttributes(attribute.String("messaging.destination.name", w.config.ResultsTopic)),
	)
	headers := make([]kafka.Header, 0, 2)
	otel.GetTextMapPropagator().Inject(publishContext, kafkaHeaderCarrier{headers: &headers})
	err = w.writer.WriteMessages(publishContext, kafka.Message{
		Key:     []byte(result.MonitorID),
		Value:   payload,
		Headers: headers,
	})
	if err != nil {
		w.metrics.ResultPublication("failure")
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		span.End()
		return fmt.Errorf("publish result: %w", err)
	}
	span.End()
	w.metrics.ResultPublication("success")
	w.metrics.Command("processed", command.Type)

	w.logger.Info(
		"check completed",
		"execution_id", result.ExecutionID,
		"monitor_id", result.MonitorID,
		"status", result.Status,
		"latency_ms", result.LatencyMS,
	)
	return nil
}

func (w *Worker) publishDeadLetter(ctx context.Context, message kafka.Message, cause error) error {
	publishContext, span := otel.Tracer("pulseops-agent/messaging").Start(
		ctx,
		"kafka publish "+w.config.CommandsDLQTopic,
		trace.WithSpanKind(trace.SpanKindProducer),
		trace.WithAttributes(attribute.String("messaging.destination.name", w.config.CommandsDLQTopic)),
	)
	defer span.End()

	headers := append([]kafka.Header(nil), message.Headers...)
	headers = append(headers,
		kafka.Header{Key: "pulseops-original-topic", Value: []byte(message.Topic)},
		kafka.Header{Key: "pulseops-original-partition", Value: []byte(strconv.Itoa(message.Partition))},
		kafka.Header{Key: "pulseops-original-offset", Value: []byte(strconv.FormatInt(message.Offset, 10))},
		kafka.Header{Key: "pulseops-error", Value: []byte(boundedError(cause))},
	)
	otel.GetTextMapPropagator().Inject(publishContext, kafkaHeaderCarrier{headers: &headers})
	err := w.dlqWriter.WriteMessages(publishContext, kafka.Message{
		Key:     message.Key,
		Value:   message.Value,
		Headers: headers,
	})
	if err != nil {
		w.metrics.DeadLetter(message.Topic, "failure")
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return err
	}
	w.metrics.DeadLetter(message.Topic, "success")
	return nil
}

func (w *Worker) commit(ctx context.Context, message kafka.Message) error {
	if err := w.reader.CommitMessages(ctx, message); err != nil {
		return fmt.Errorf("commit command: %w", err)
	}
	return nil
}

func waitForRetry(ctx context.Context, initialBackoff, maxBackoff time.Duration, failedAttempt int) error {
	backoff := initialBackoff * time.Duration(1<<min(failedAttempt-1, 6))
	if backoff > maxBackoff {
		backoff = maxBackoff
	}
	timer := time.NewTimer(backoff)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

func (w *Worker) runHeartbeats(ctx context.Context) {
	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()
	for {
		if err := w.publishHeartbeat(ctx); err != nil && ctx.Err() == nil {
			w.metrics.Heartbeat("failure")
			w.logger.Error("heartbeat publish failed", "error", err)
		} else if err == nil {
			w.metrics.Heartbeat("success")
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func (w *Worker) publishHeartbeat(ctx context.Context) error {
	payload, err := json.Marshal(model.AgentHeartbeat{
		AgentID:  w.config.AgentID,
		Location: w.config.Location,
		Version:  w.config.Version,
		SentAt:   time.Now().UTC(),
	})
	if err != nil {
		return fmt.Errorf("encode heartbeat: %w", err)
	}
	publishContext, span := otel.Tracer("pulseops-agent/messaging").Start(
		ctx,
		"kafka publish "+w.config.HeartbeatsTopic,
		trace.WithSpanKind(trace.SpanKindProducer),
		trace.WithAttributes(attribute.String("messaging.destination.name", w.config.HeartbeatsTopic)),
	)
	defer span.End()
	headers := make([]kafka.Header, 0, 2)
	otel.GetTextMapPropagator().Inject(publishContext, kafkaHeaderCarrier{headers: &headers})
	if err := w.heartbeatWriter.WriteMessages(publishContext, kafka.Message{
		Key:     []byte(w.config.AgentID),
		Value:   payload,
		Headers: headers,
	}); err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
		return err
	}
	return nil
}

func (w *Worker) close() {
	if err := w.reader.Close(); err != nil {
		w.logger.Error("reader close failed", "error", err)
	}
	if err := w.writer.Close(); err != nil {
		w.logger.Error("writer close failed", "error", err)
	}
	if err := w.dlqWriter.Close(); err != nil {
		w.logger.Error("dead-letter writer close failed", "error", err)
	}
	if err := w.heartbeatWriter.Close(); err != nil {
		w.logger.Error("heartbeat writer close failed", "error", err)
	}
}

type kafkaHeaderCarrier struct {
	headers *[]kafka.Header
}

var _ propagation.TextMapCarrier = kafkaHeaderCarrier{}

func (c kafkaHeaderCarrier) Get(key string) string {
	for index := len(*c.headers) - 1; index >= 0; index-- {
		if strings.EqualFold((*c.headers)[index].Key, key) {
			return string((*c.headers)[index].Value)
		}
	}
	return ""
}

func (c kafkaHeaderCarrier) Set(key, value string) {
	for index := range *c.headers {
		if strings.EqualFold((*c.headers)[index].Key, key) {
			(*c.headers)[index].Value = []byte(value)
			return
		}
	}
	*c.headers = append(*c.headers, kafka.Header{Key: key, Value: []byte(value)})
}

func (c kafkaHeaderCarrier) Keys() []string {
	keys := make([]string, 0, len(*c.headers))
	for _, header := range *c.headers {
		keys = append(keys, header.Key)
	}
	return keys
}

func boundedError(err error) string {
	if err == nil {
		return "unknown error"
	}
	const maxLength = 512
	message := err.Error()
	if len(message) > maxLength {
		return message[:maxLength]
	}
	return message
}
