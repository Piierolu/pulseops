package model

import "time"

type CheckCommand struct {
	ExecutionID   string        `json:"executionId"`
	MonitorID     string        `json:"monitorId"`
	Type          string        `json:"type"`
	Location      string        `json:"location"`
	ScheduledAt   time.Time     `json:"scheduledAt"`
	TimeoutMS     int           `json:"timeoutMs"`
	Configuration Configuration `json:"configuration"`
}

type Configuration struct {
	URL               string `json:"url"`
	ExpectedStatus    int    `json:"expectedStatus"`
	Host              string `json:"host"`
	Port              int    `json:"port"`
	RecordType        string `json:"recordType"`
	ExpectedValue     string `json:"expectedValue"`
	ExpiryWarningDays int    `json:"expiryWarningDays"`
}

type CheckResult struct {
	ExecutionID string         `json:"executionId"`
	MonitorID   string         `json:"monitorId"`
	AgentID     string         `json:"agentId"`
	Location    string         `json:"location"`
	Status      string         `json:"status"`
	LatencyMS   int64          `json:"latencyMs"`
	StatusCode  *int           `json:"statusCode"`
	Error       *string        `json:"error"`
	Details     map[string]any `json:"details"`
	CheckedAt   time.Time      `json:"checkedAt"`
}

type AgentHeartbeat struct {
	AgentID  string    `json:"agentId"`
	Location string    `json:"location"`
	Version  string    `json:"version"`
	SentAt   time.Time `json:"sentAt"`
}
