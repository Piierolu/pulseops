package telemetry

import (
	"context"
	"strings"

	"github.com/prometheus/client_golang/prometheus"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.30.0"
)

type AgentMetrics struct {
	commands           *prometheus.CounterVec
	checkDuration      *prometheus.HistogramVec
	resultPublications *prometheus.CounterVec
	heartbeats         *prometheus.CounterVec
	retries            *prometheus.CounterVec
	deadLetters        *prometheus.CounterVec
	inflight           prometheus.Gauge
}

func NewAgentMetrics(registerer prometheus.Registerer) *AgentMetrics {
	metrics := &AgentMetrics{
		commands: prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "pulseops_agent_commands_total",
			Help: "Commands handled by outcome and check type.",
		}, []string{"outcome", "type"}),
		checkDuration: prometheus.NewHistogramVec(prometheus.HistogramOpts{
			Name:    "pulseops_agent_check_duration_seconds",
			Help:    "Check execution time by type and status.",
			Buckets: prometheus.DefBuckets,
		}, []string{"type", "status"}),
		resultPublications: prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "pulseops_agent_result_publish_total",
			Help: "Result publication attempts by outcome.",
		}, []string{"outcome"}),
		heartbeats: prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "pulseops_agent_heartbeat_publish_total",
			Help: "Heartbeat publication attempts by outcome.",
		}, []string{"outcome"}),
		retries: prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "pulseops_agent_kafka_retries_total",
			Help: "Kafka processing retries by source topic.",
		}, []string{"topic"}),
		deadLetters: prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "pulseops_agent_kafka_dlq_total",
			Help: "Dead-letter publication attempts by source topic and outcome.",
		}, []string{"topic", "outcome"}),
		inflight: prometheus.NewGauge(prometheus.GaugeOpts{
			Name: "pulseops_agent_inflight_checks",
			Help: "Checks currently being executed.",
		}),
	}
	registerer.MustRegister(
		metrics.commands,
		metrics.checkDuration,
		metrics.resultPublications,
		metrics.heartbeats,
		metrics.retries,
		metrics.deadLetters,
		metrics.inflight,
	)
	return metrics
}

func (m *AgentMetrics) Command(outcome, checkType string) {
	m.commands.WithLabelValues(outcome, normalizedType(checkType)).Inc()
}

func (m *AgentMetrics) ObserveCheck(checkType, status string, seconds float64) {
	m.checkDuration.WithLabelValues(normalizedType(checkType), strings.ToUpper(status)).Observe(seconds)
}

func (m *AgentMetrics) ResultPublication(outcome string) {
	m.resultPublications.WithLabelValues(outcome).Inc()
}

func (m *AgentMetrics) Heartbeat(outcome string) {
	m.heartbeats.WithLabelValues(outcome).Inc()
}

func (m *AgentMetrics) Retry(topic string) {
	m.retries.WithLabelValues(topic).Inc()
}

func (m *AgentMetrics) DeadLetter(topic, outcome string) {
	m.deadLetters.WithLabelValues(topic, outcome).Inc()
}

func (m *AgentMetrics) InflightInc() {
	m.inflight.Inc()
}

func (m *AgentMetrics) InflightDec() {
	m.inflight.Dec()
}

func SetupTracing(
	ctx context.Context,
	serviceName, serviceVersion, endpoint string,
	sampleRatio float64,
) (func(context.Context) error, error) {
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))
	if strings.TrimSpace(endpoint) == "" {
		return func(context.Context) error { return nil }, nil
	}

	exporter, err := otlptracehttp.New(
		ctx,
		otlptracehttp.WithEndpointURL(strings.TrimRight(endpoint, "/")+"/v1/traces"),
	)
	if err != nil {
		return nil, err
	}
	serviceResource, err := resource.New(
		ctx,
		resource.WithFromEnv(),
		resource.WithTelemetrySDK(),
		resource.WithAttributes(
			semconv.ServiceName(serviceName),
			semconv.ServiceVersion(serviceVersion),
			attribute.String("deployment.environment.name", "local"),
		),
	)
	if err != nil {
		return nil, err
	}
	provider := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter),
		sdktrace.WithResource(serviceResource),
		sdktrace.WithSampler(sdktrace.ParentBased(sdktrace.TraceIDRatioBased(sampleRatio))),
	)
	otel.SetTracerProvider(provider)
	return provider.Shutdown, nil
}

func normalizedType(checkType string) string {
	value := strings.ToUpper(strings.TrimSpace(checkType))
	if value == "" {
		return "UNKNOWN"
	}
	return value
}
