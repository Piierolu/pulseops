package checks

import (
	"context"
	"fmt"
	"net"
	"strconv"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
)

type TCPChecker struct {
	dialContext func(context.Context, string, string) (net.Conn, error)
}

func NewTCPChecker(allowPrivateTargets bool) *TCPChecker {
	dialer := &net.Dialer{Timeout: 10 * time.Second, KeepAlive: 30 * time.Second}
	return &TCPChecker{dialContext: safeDialContext(dialer, allowPrivateTargets)}
}

func (c *TCPChecker) Execute(ctx context.Context, command model.CheckCommand, agentID string) model.CheckResult {
	startedAt := time.Now()
	result := newResult(command, agentID)
	if command.Configuration.Host == "" || command.Configuration.Port < 1 {
		return finish(result, startedAt, nil, fmt.Errorf("invalid TCP target"))
	}

	checkContext, cancel := context.WithTimeout(ctx, time.Duration(command.TimeoutMS)*time.Millisecond)
	defer cancel()
	address := net.JoinHostPort(command.Configuration.Host, strconv.Itoa(command.Configuration.Port))
	connection, err := c.dialContext(checkContext, "tcp", address)
	if err != nil {
		return finish(result, startedAt, nil, err)
	}
	_ = connection.Close()

	result.Status = "SUCCESS"
	result.Details["address"] = address
	return finish(result, startedAt, nil, nil)
}
