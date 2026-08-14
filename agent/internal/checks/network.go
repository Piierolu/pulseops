package checks

import (
	"context"
	"errors"
	"fmt"
	"net"
	"strings"
)

func safeDialContext(dialer *net.Dialer, allowPrivateTargets bool) func(context.Context, string, string) (net.Conn, error) {
	return func(ctx context.Context, network, address string) (net.Conn, error) {
		host, port, err := net.SplitHostPort(address)
		if err != nil {
			return nil, fmt.Errorf("invalid target address: %w", err)
		}
		addresses, err := net.DefaultResolver.LookupIPAddr(ctx, host)
		if err != nil {
			return nil, fmt.Errorf("resolve target: %w", err)
		}
		if len(addresses) == 0 {
			return nil, errors.New("target resolved to no addresses")
		}

		for _, address := range addresses {
			if !allowPrivateTargets && isRestricted(address.IP) {
				continue
			}
			return dialer.DialContext(ctx, network, net.JoinHostPort(address.IP.String(), port))
		}
		return nil, fmt.Errorf("target %s resolves only to restricted addresses", strings.ToLower(host))
	}
}

func isRestricted(ip net.IP) bool {
	return ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() ||
		ip.IsLinkLocalMulticast() || ip.IsUnspecified() || ip.IsMulticast()
}
