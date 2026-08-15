package dlq

import (
	"testing"
	"time"

	"github.com/segmentio/kafka-go"
)

func TestValidatePayloadRejectsMalformedCommand(t *testing.T) {
	if err := validatePayload("check.commands.v1.dlq", []byte("not-json")); err == nil {
		t.Fatal("validatePayload accepted malformed JSON")
	}
}

func TestValidatePayloadAcceptsCompleteHeartbeat(t *testing.T) {
	payload := []byte(`{"agentId":"local-01","location":"local","version":"0.4.0","sentAt":"2026-08-14T12:00:00Z"}`)

	if err := validatePayload("agent.heartbeats.v1.dlq", payload); err != nil {
		t.Fatalf("validatePayload returned %v", err)
	}
}

func TestSanitizedHeadersPreservesTraceAndRemovesRecoveryMetadata(t *testing.T) {
	headers := []kafka.Header{
		{Key: "traceparent", Value: []byte("trace")},
		{Key: "pulseops-original-topic", Value: []byte("source")},
		{Key: "kafka_dlt-exception-message", Value: []byte("failure")},
		{Key: "pulseops-error", Value: []byte("failure")},
	}

	result := sanitizedHeaders(headers)

	if len(result) != 1 || result[0].Key != "traceparent" {
		t.Fatalf("sanitized headers = %#v, want only traceparent", result)
	}
}

func TestInspectDoesNotExposePayloadByDefault(t *testing.T) {
	message := kafka.Message{
		Value: []byte("secret"),
		Time:  time.Now(),
	}

	result, err := inspect(message, Coordinate{Topic: "check.results.v1.dlq", Partition: 0, Offset: 1})
	if err != nil {
		t.Fatal(err)
	}
	if result.Payload != "" {
		t.Fatalf("payload = %q, want hidden", result.Payload)
	}
	if result.PayloadSHA256 == "" {
		t.Fatal("payload hash is empty")
	}
}
