package telemetry

import (
	"testing"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/testutil"
)

func TestAgentMetricsUseBoundedLabels(t *testing.T) {
	registry := prometheus.NewRegistry()
	metrics := NewAgentMetrics(registry)

	metrics.Command("processed", "http")
	metrics.ResultPublication("success")
	metrics.Retry("check.commands.v1")

	if got := testutil.ToFloat64(metrics.commands.WithLabelValues("processed", "HTTP")); got != 1 {
		t.Fatalf("command counter = %v, want 1", got)
	}
	if got := testutil.ToFloat64(metrics.resultPublications.WithLabelValues("success")); got != 1 {
		t.Fatalf("result publication counter = %v, want 1", got)
	}
	if got := testutil.ToFloat64(metrics.retries.WithLabelValues("check.commands.v1")); got != 1 {
		t.Fatalf("retry counter = %v, want 1", got)
	}
}
