package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/pulseops/pulseops/agent/internal/checks"
	"github.com/pulseops/pulseops/agent/internal/messaging"
	"github.com/pulseops/pulseops/agent/internal/telemetry"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	config, allowPrivateTargets, err := loadConfig()
	if err != nil {
		logger.Error("invalid configuration", "error", err)
		os.Exit(1)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	traceSampleRatio, err := strconv.ParseFloat(valueOrDefault("OTEL_TRACE_SAMPLE_RATIO", "1.0"), 64)
	if err != nil || traceSampleRatio < 0 || traceSampleRatio > 1 {
		logger.Error("OTEL_TRACE_SAMPLE_RATIO must be between 0.0 and 1.0")
		os.Exit(1)
	}

	shutdownTracing, err := telemetry.SetupTracing(
		ctx,
		"pulseops-agent",
		config.Version,
		valueOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", ""),
		traceSampleRatio,
	)
	if err != nil {
		logger.Error("tracing initialization failed", "error", err)
		os.Exit(1)
	}
	defer func() {
		shutdownContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := shutdownTracing(shutdownContext); err != nil {
			logger.Error("tracing shutdown failed", "error", err)
		}
	}()

	metrics := telemetry.NewAgentMetrics(prometheus.DefaultRegisterer)
	metricsMux := http.NewServeMux()
	metricsMux.Handle("/metrics", promhttp.Handler())
	metricsMux.HandleFunc("/healthz", func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusOK)
		_, _ = response.Write([]byte("ok\n"))
	})
	metricsMux.HandleFunc("/readyz", func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusOK)
		_, _ = response.Write([]byte("ok\n"))
	})
	metricsServer := &http.Server{
		Addr:              valueOrDefault("METRICS_ADDRESS", ":9464"),
		Handler:           metricsMux,
		ReadHeaderTimeout: 5 * time.Second,
	}
	go func() {
		if err := metricsServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("metrics server failed", "error", err)
			stop()
		}
	}()
	defer func() {
		shutdownContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := metricsServer.Shutdown(shutdownContext); err != nil {
			logger.Error("metrics server shutdown failed", "error", err)
		}
	}()

	worker := messaging.NewWorker(config, checks.NewRunner(allowPrivateTargets), metrics, logger)
	if err := worker.Run(ctx); err != nil {
		logger.Error("agent stopped unexpectedly", "error", err)
		os.Exit(1)
	}
}

func loadConfig() (messaging.Config, bool, error) {
	brokers := strings.Split(valueOrDefault("KAFKA_BROKERS", "localhost:9092"), ",")
	agentID := strings.TrimSpace(os.Getenv("AGENT_ID"))
	location := strings.TrimSpace(os.Getenv("AGENT_LOCATION"))
	if agentID == "" || location == "" {
		return messaging.Config{}, false, fmt.Errorf("AGENT_ID and AGENT_LOCATION are required")
	}
	allowPrivateTargets, err := strconv.ParseBool(valueOrDefault("ALLOW_PRIVATE_TARGETS", "false"))
	if err != nil {
		return messaging.Config{}, false, fmt.Errorf("parse ALLOW_PRIVATE_TARGETS: %w", err)
	}

	retryMaxAttempts, err := strconv.Atoi(valueOrDefault("KAFKA_RETRY_MAX_ATTEMPTS", "3"))
	if err != nil || retryMaxAttempts < 1 {
		return messaging.Config{}, false, fmt.Errorf("KAFKA_RETRY_MAX_ATTEMPTS must be a positive integer")
	}
	retryInitialBackoff, err := time.ParseDuration(valueOrDefault("KAFKA_RETRY_INITIAL_BACKOFF", "500ms"))
	if err != nil || retryInitialBackoff <= 0 {
		return messaging.Config{}, false, fmt.Errorf("KAFKA_RETRY_INITIAL_BACKOFF must be a positive duration")
	}
	retryMaxBackoff, err := time.ParseDuration(valueOrDefault("KAFKA_RETRY_MAX_BACKOFF", "5s"))
	if err != nil || retryMaxBackoff < retryInitialBackoff {
		return messaging.Config{}, false, fmt.Errorf("KAFKA_RETRY_MAX_BACKOFF must not be shorter than the initial backoff")
	}

	return messaging.Config{
		Brokers:             brokers,
		CommandsTopic:       valueOrDefault("COMMANDS_TOPIC", "check.commands.v1"),
		CommandsDLQTopic:    valueOrDefault("COMMANDS_DLQ_TOPIC", "check.commands.v1.dlq"),
		ResultsTopic:        valueOrDefault("RESULTS_TOPIC", "check.results.v1"),
		HeartbeatsTopic:     valueOrDefault("HEARTBEATS_TOPIC", "agent.heartbeats.v1"),
		AgentID:             agentID,
		Location:            location,
		Version:             valueOrDefault("AGENT_VERSION", "0.4.0"),
		RetryMaxAttempts:    retryMaxAttempts,
		RetryInitialBackoff: retryInitialBackoff,
		RetryMaxBackoff:     retryMaxBackoff,
	}, allowPrivateTargets, nil
}

func valueOrDefault(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}
