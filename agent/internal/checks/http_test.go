package checks

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
)

func TestHTTPCheckerReturnsSuccess(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	checker := NewHTTPChecker(true)
	result := checker.Execute(context.Background(), command(server.URL, http.StatusNoContent), "local-01")

	if result.Status != "SUCCESS" {
		t.Fatalf("expected SUCCESS, got %s: %v", result.Status, result.Error)
	}
	if result.StatusCode == nil || *result.StatusCode != http.StatusNoContent {
		t.Fatalf("expected status %d, got %v", http.StatusNoContent, result.StatusCode)
	}
}

func TestHTTPCheckerRejectsUnexpectedStatus(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()

	checker := NewHTTPChecker(true)
	result := checker.Execute(context.Background(), command(server.URL, http.StatusOK), "local-01")

	if result.Status != "FAILURE" || result.Error == nil {
		t.Fatalf("expected a failed result, got %#v", result)
	}
}

func TestHTTPCheckerRejectsPrivateTargetsByDefault(t *testing.T) {
	checker := NewHTTPChecker(false)
	result := checker.Execute(context.Background(), command("http://127.0.0.1:8080", http.StatusOK), "local-01")

	if result.Error == nil || !strings.Contains(*result.Error, "restricted addresses") {
		t.Fatalf("expected restricted address error, got %#v", result.Error)
	}
}

func command(target string, expectedStatus int) model.CheckCommand {
	return model.CheckCommand{
		ExecutionID: "execution-1",
		MonitorID:   "monitor-1",
		Type:        "HTTP",
		Location:    "local",
		ScheduledAt: time.Now(),
		TimeoutMS:   1000,
		Configuration: model.Configuration{
			URL:            target,
			ExpectedStatus: expectedStatus,
		},
	}
}
