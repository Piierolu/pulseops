package checks

import (
	"context"
	"crypto/x509"
	"net"
	"net/http/httptest"
	"net/url"
	"strconv"
	"testing"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
)

func TestTCPCheckerConnectsToOpenPort(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	port := listener.Addr().(*net.TCPAddr).Port

	command := protocolCommand("TCP")
	command.Configuration.Host = "127.0.0.1"
	command.Configuration.Port = port
	result := NewTCPChecker(true).Execute(context.Background(), command, "local-01")

	if result.Status != "SUCCESS" {
		t.Fatalf("expected TCP success, got %#v", result)
	}
}

func TestDNSCheckerResolvesARecord(t *testing.T) {
	command := protocolCommand("DNS")
	command.Configuration.Host = "localhost"
	command.Configuration.RecordType = "A"
	result := NewDNSChecker().Execute(context.Background(), command, "local-01")

	if result.Status != "SUCCESS" {
		t.Fatalf("expected DNS success, got %#v", result)
	}
	if result.Details["values"] == nil {
		t.Fatal("expected resolved DNS values")
	}
}

func TestTLSCheckerValidatesCertificate(t *testing.T) {
	server := httptest.NewTLSServer(nil)
	defer server.Close()
	target, err := url.Parse(server.URL)
	if err != nil {
		t.Fatal(err)
	}
	_, portText, err := net.SplitHostPort(target.Host)
	if err != nil {
		t.Fatal(err)
	}
	port, err := strconv.Atoi(portText)
	if err != nil {
		t.Fatal(err)
	}
	roots := x509.NewCertPool()
	roots.AddCert(server.Certificate())
	command := protocolCommand("TLS")
	command.Configuration.Host = target.Hostname()
	command.Configuration.Port = port
	command.Configuration.ExpiryWarningDays = 1

	result := NewTLSChecker(true, roots).Execute(context.Background(), command, "local-01")

	if result.Status != "SUCCESS" {
		t.Fatalf("expected TLS success, got %#v", result)
	}
	if result.Details["expiresAt"] == nil {
		t.Fatal("expected certificate expiry details")
	}
}

func protocolCommand(checkType string) model.CheckCommand {
	return model.CheckCommand{
		ExecutionID: "execution-protocol",
		MonitorID:   "monitor-protocol",
		Type:        checkType,
		Location:    "local",
		ScheduledAt: time.Now(),
		TimeoutMS:   1000,
	}
}
