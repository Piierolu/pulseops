package messaging

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/segmentio/kafka-go"
)

func TestKafkaHeaderCarrierReplacesTraceHeader(t *testing.T) {
	headers := []kafka.Header{
		{Key: "traceparent", Value: []byte("old")},
		{Key: "custom", Value: []byte("preserved")},
	}
	carrier := kafkaHeaderCarrier{headers: &headers}

	carrier.Set("TraceParent", "new")
	carrier.Set("tracestate", "vendor=value")

	if got := carrier.Get("traceparent"); got != "new" {
		t.Fatalf("traceparent = %q, want new", got)
	}
	if got := carrier.Get("custom"); got != "preserved" {
		t.Fatalf("custom header = %q, want preserved", got)
	}
	if len(headers) != 3 {
		t.Fatalf("header count = %d, want 3", len(headers))
	}
}

func TestWaitForRetryHonorsCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	err := waitForRetry(ctx, time.Minute, time.Minute, 1)

	if !errors.Is(err, context.Canceled) {
		t.Fatalf("waitForRetry error = %v, want context.Canceled", err)
	}
}

func TestBoundedErrorLimitsKafkaHeader(t *testing.T) {
	message := strings.Repeat("x", 600)

	if got := boundedError(errors.New(message)); len(got) != 512 {
		t.Fatalf("bounded error length = %d, want 512", len(got))
	}
}
