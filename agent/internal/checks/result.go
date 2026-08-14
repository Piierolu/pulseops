package checks

import (
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
)

func newResult(command model.CheckCommand, agentID string) model.CheckResult {
	return model.CheckResult{
		ExecutionID: command.ExecutionID,
		MonitorID:   command.MonitorID,
		AgentID:     agentID,
		Location:    command.Location,
		Status:      "FAILURE",
		Details:     map[string]any{},
	}
}

func finish(result model.CheckResult, startedAt time.Time, statusCode *int, checkError error) model.CheckResult {
	result.LatencyMS = max(time.Since(startedAt).Milliseconds(), 0)
	result.StatusCode = statusCode
	result.CheckedAt = time.Now().UTC()
	if checkError != nil {
		message := checkError.Error()
		if len(message) > 2048 {
			message = message[:2048]
		}
		result.Error = &message
	}
	return result
}
