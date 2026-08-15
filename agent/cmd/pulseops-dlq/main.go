package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/pulseops/pulseops/agent/internal/dlq"
)

func main() {
	if len(os.Args) < 2 {
		fail(errors.New("usage: pulseops-dlq inspect|redrive [flags]"))
	}
	var err error
	switch os.Args[1] {
	case "inspect":
		err = inspect(os.Args[2:])
	case "redrive":
		err = redrive(os.Args[2:])
	default:
		err = fmt.Errorf("unknown operation %q", os.Args[1])
	}
	if err != nil {
		fail(err)
	}
}

func inspect(arguments []string) error {
	flags := flag.NewFlagSet("inspect", flag.ContinueOnError)
	common := addCommonFlags(flags)
	showPayload := flags.Bool("show-payload", false, "include the original payload in output")
	if err := flags.Parse(arguments); err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	result, err := dlq.NewOperator(common.brokerList()).Inspect(
		ctx,
		common.coordinate(),
		*showPayload,
		common.maxBytes,
	)
	if err != nil {
		return err
	}
	return printJSON(result)
}

func redrive(arguments []string) error {
	flags := flag.NewFlagSet("redrive", flag.ContinueOnError)
	common := addCommonFlags(flags)
	expectedHash := flags.String("sha256", "", "required SHA-256 of the exact payload")
	operator := flags.String("operator", "", "operator performing the redrive")
	reason := flags.String("reason", "", "auditable redrive reason")
	execute := flags.Bool("execute", false, "publish after validation; otherwise dry-run")
	if err := flags.Parse(arguments); err != nil {
		return err
	}
	if strings.TrimSpace(*expectedHash) == "" {
		return errors.New("--sha256 is required")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	result, err := dlq.NewOperator(common.brokerList()).Redrive(ctx, dlq.RedriveRequest{
		Coordinate:      common.coordinate(),
		ExpectedSHA256:  *expectedHash,
		Operator:        *operator,
		Reason:          *reason,
		Execute:         *execute,
		MaxPayloadBytes: common.maxBytes,
	})
	if err != nil {
		return err
	}
	if !*execute {
		fmt.Fprintln(os.Stderr, "dry-run only; add --execute to publish")
	}
	return printJSON(result)
}

type commonFlags struct {
	brokers   string
	topic     string
	partition int
	offset    int64
	maxBytes  int
}

func addCommonFlags(flags *flag.FlagSet) *commonFlags {
	values := &commonFlags{}
	flags.StringVar(&values.brokers, "brokers", envOrDefault("KAFKA_BROKERS", "localhost:9092"), "comma-separated Kafka brokers")
	flags.StringVar(&values.topic, "topic", "", "exact allowlisted DLQ topic")
	flags.IntVar(&values.partition, "partition", -1, "exact Kafka partition")
	flags.Int64Var(&values.offset, "offset", -1, "exact Kafka offset")
	flags.IntVar(&values.maxBytes, "max-bytes", dlq.DefaultMaxPayloadBytes, "maximum payload size")
	return values
}

func (flags *commonFlags) brokerList() []string {
	return strings.Split(flags.brokers, ",")
}

func (flags *commonFlags) coordinate() dlq.Coordinate {
	return dlq.Coordinate{Topic: flags.topic, Partition: flags.partition, Offset: flags.offset}
}

func envOrDefault(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

func printJSON(value any) error {
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetIndent("", "  ")
	return encoder.Encode(value)
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, "pulseops-dlq:", err)
	os.Exit(1)
}
