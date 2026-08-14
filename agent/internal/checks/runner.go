package checks

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
)

type Runner struct {
	http *HTTPChecker
	tcp  *TCPChecker
	dns  *DNSChecker
	tls  *TLSChecker
}

func NewRunner(allowPrivateTargets bool) *Runner {
	return &Runner{
		http: NewHTTPChecker(allowPrivateTargets),
		tcp:  NewTCPChecker(allowPrivateTargets),
		dns:  NewDNSChecker(),
		tls:  NewTLSChecker(allowPrivateTargets, nil),
	}
}

func (r *Runner) Execute(ctx context.Context, command model.CheckCommand, agentID string) model.CheckResult {
	ctx, span := otel.Tracer("pulseops-agent/checks").Start(ctx, "check.execute")
	span.SetAttributes(
		attribute.String("pulseops.check.type", strings.ToUpper(command.Type)),
		attribute.String("pulseops.monitor.id", command.MonitorID),
		attribute.String("pulseops.execution.id", command.ExecutionID),
	)
	defer span.End()

	var result model.CheckResult
	switch strings.ToUpper(command.Type) {
	case "HTTP":
		result = r.http.Execute(ctx, command, agentID)
	case "TCP":
		result = r.tcp.Execute(ctx, command, agentID)
	case "DNS":
		result = r.dns.Execute(ctx, command, agentID)
	case "TLS":
		result = r.tls.Execute(ctx, command, agentID)
	default:
		result = finish(newResult(command, agentID), time.Now(), nil, fmt.Errorf("unsupported check type %q", command.Type))
	}
	span.SetAttributes(attribute.String("pulseops.check.status", result.Status))
	if result.Error != nil {
		span.SetStatus(codes.Error, *result.Error)
	}
	return result
}
