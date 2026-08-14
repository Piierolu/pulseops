package checks

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

const maxResponseBytes = 1 << 20

type HTTPChecker struct {
	client *http.Client
}

func NewHTTPChecker(allowPrivateTargets bool) *HTTPChecker {
	dialer := &net.Dialer{Timeout: 10 * time.Second, KeepAlive: 30 * time.Second}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.MaxIdleConns = 100
	transport.MaxIdleConnsPerHost = 10
	transport.TLSClientConfig = &tls.Config{MinVersion: tls.VersionTLS12}
	transport.DialContext = safeDialContext(dialer, allowPrivateTargets)

	return &HTTPChecker{
		client: &http.Client{
			Transport: otelhttp.NewTransport(transport),
			CheckRedirect: func(_ *http.Request, via []*http.Request) error {
				if len(via) >= 5 {
					return errors.New("too many redirects")
				}
				return nil
			},
		},
	}
}

func (c *HTTPChecker) Execute(ctx context.Context, command model.CheckCommand, agentID string) model.CheckResult {
	startedAt := time.Now()
	result := newResult(command, agentID)

	target, err := url.Parse(command.Configuration.URL)
	if err != nil || target.Hostname() == "" || (target.Scheme != "http" && target.Scheme != "https") {
		return finish(result, startedAt, nil, errors.New("invalid HTTP target"))
	}

	requestContext, cancel := context.WithTimeout(ctx, time.Duration(command.TimeoutMS)*time.Millisecond)
	defer cancel()
	request, err := http.NewRequestWithContext(requestContext, http.MethodGet, target.String(), nil)
	if err != nil {
		return finish(result, startedAt, nil, err)
	}
	request.Header.Set("User-Agent", "PulseOps-Agent/0.1")

	response, err := c.client.Do(request)
	if err != nil {
		return finish(result, startedAt, nil, err)
	}
	defer response.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, maxResponseBytes))

	statusCode := response.StatusCode
	result.Details["contentLength"] = response.ContentLength
	result.Details["protocol"] = response.Proto
	if statusCode != command.Configuration.ExpectedStatus {
		return finish(result, startedAt, &statusCode, fmt.Errorf(
			"unexpected HTTP status: got %d, expected %d",
			statusCode,
			command.Configuration.ExpectedStatus,
		))
	}

	result.Status = "SUCCESS"
	return finish(result, startedAt, &statusCode, nil)
}
