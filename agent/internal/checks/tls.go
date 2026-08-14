package checks

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"net"
	"strconv"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
)

type TLSChecker struct {
	dialContext func(context.Context, string, string) (net.Conn, error)
	rootCAs     *x509.CertPool
}

func NewTLSChecker(allowPrivateTargets bool, rootCAs *x509.CertPool) *TLSChecker {
	dialer := &net.Dialer{Timeout: 10 * time.Second, KeepAlive: 30 * time.Second}
	return &TLSChecker{
		dialContext: safeDialContext(dialer, allowPrivateTargets),
		rootCAs:     rootCAs,
	}
}

func (c *TLSChecker) Execute(ctx context.Context, command model.CheckCommand, agentID string) model.CheckResult {
	startedAt := time.Now()
	result := newResult(command, agentID)
	if command.Configuration.Host == "" || command.Configuration.Port < 1 {
		return finish(result, startedAt, nil, fmt.Errorf("invalid TLS target"))
	}

	checkContext, cancel := context.WithTimeout(ctx, time.Duration(command.TimeoutMS)*time.Millisecond)
	defer cancel()
	address := net.JoinHostPort(command.Configuration.Host, strconv.Itoa(command.Configuration.Port))
	connection, err := c.dialContext(checkContext, "tcp", address)
	if err != nil {
		return finish(result, startedAt, nil, err)
	}
	defer connection.Close()
	tlsConnection := tls.Client(connection, &tls.Config{
		ServerName: command.Configuration.Host,
		MinVersion: tls.VersionTLS12,
		RootCAs:    c.rootCAs,
	})
	if err := tlsConnection.HandshakeContext(checkContext); err != nil {
		return finish(result, startedAt, nil, fmt.Errorf("TLS handshake failed: %w", err))
	}

	state := tlsConnection.ConnectionState()
	if len(state.PeerCertificates) == 0 {
		return finish(result, startedAt, nil, fmt.Errorf("server returned no certificate"))
	}
	certificate := state.PeerCertificates[0]
	daysRemaining := int(time.Until(certificate.NotAfter).Hours() / 24)
	result.Details["expiresAt"] = certificate.NotAfter.UTC().Format(time.RFC3339)
	result.Details["daysRemaining"] = daysRemaining
	result.Details["issuer"] = certificate.Issuer.CommonName
	result.Details["tlsVersion"] = tlsVersionName(state.Version)
	if daysRemaining < command.Configuration.ExpiryWarningDays {
		return finish(result, startedAt, nil, fmt.Errorf(
			"certificate expires in %d days, below warning threshold %d",
			daysRemaining,
			command.Configuration.ExpiryWarningDays,
		))
	}

	result.Status = "SUCCESS"
	return finish(result, startedAt, nil, nil)
}

func tlsVersionName(version uint16) string {
	switch version {
	case tls.VersionTLS13:
		return "TLS 1.3"
	case tls.VersionTLS12:
		return "TLS 1.2"
	default:
		return fmt.Sprintf("0x%04x", version)
	}
}
