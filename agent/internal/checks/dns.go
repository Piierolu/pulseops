package checks

import (
	"context"
	"fmt"
	"net"
	"sort"
	"strings"
	"time"

	"github.com/pulseops/pulseops/agent/internal/model"
)

type DNSChecker struct {
	resolver *net.Resolver
}

func NewDNSChecker() *DNSChecker {
	return &DNSChecker{resolver: net.DefaultResolver}
}

func (c *DNSChecker) Execute(ctx context.Context, command model.CheckCommand, agentID string) model.CheckResult {
	startedAt := time.Now()
	result := newResult(command, agentID)
	if command.Configuration.Host == "" {
		return finish(result, startedAt, nil, fmt.Errorf("invalid DNS target"))
	}

	checkContext, cancel := context.WithTimeout(ctx, time.Duration(command.TimeoutMS)*time.Millisecond)
	defer cancel()
	values, err := c.lookup(checkContext, command.Configuration.Host, command.Configuration.RecordType)
	if err != nil {
		return finish(result, startedAt, nil, err)
	}
	if expected := normalizeDNSValue(command.Configuration.ExpectedValue); expected != "" {
		matched := false
		for _, value := range values {
			if normalizeDNSValue(value) == expected {
				matched = true
				break
			}
		}
		if !matched {
			return finish(result, startedAt, nil, fmt.Errorf("expected DNS value %q was not found", command.Configuration.ExpectedValue))
		}
	}

	result.Status = "SUCCESS"
	result.Details["recordType"] = strings.ToUpper(command.Configuration.RecordType)
	result.Details["values"] = values
	return finish(result, startedAt, nil, nil)
}

func (c *DNSChecker) lookup(ctx context.Context, host, recordType string) ([]string, error) {
	var values []string
	var err error
	switch strings.ToUpper(recordType) {
	case "A":
		var addresses []net.IP
		addresses, err = c.resolver.LookupIP(ctx, "ip4", host)
		for _, address := range addresses {
			values = append(values, address.String())
		}
	case "AAAA":
		var addresses []net.IP
		addresses, err = c.resolver.LookupIP(ctx, "ip6", host)
		for _, address := range addresses {
			values = append(values, address.String())
		}
	case "CNAME":
		var cname string
		cname, err = c.resolver.LookupCNAME(ctx, host)
		if err == nil {
			values = []string{cname}
		}
	case "TXT":
		values, err = c.resolver.LookupTXT(ctx, host)
	default:
		return nil, fmt.Errorf("unsupported DNS record type %q", recordType)
	}
	if err != nil {
		return nil, fmt.Errorf("DNS lookup failed: %w", err)
	}
	if len(values) == 0 {
		return nil, fmt.Errorf("DNS lookup returned no records")
	}
	sort.Strings(values)
	return values, nil
}

func normalizeDNSValue(value string) string {
	return strings.TrimSuffix(strings.ToLower(strings.TrimSpace(value)), ".")
}
