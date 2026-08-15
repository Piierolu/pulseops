FROM golang:1.23-alpine AS dependencies
WORKDIR /workspace
COPY go.mod go.sum ./
RUN go mod download

FROM dependencies AS test
COPY . .
RUN go test ./...

FROM dependencies AS build
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /pulseops-agent ./cmd/pulseops-agent && \
    CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /pulseops-dlq ./cmd/pulseops-dlq

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /pulseops-agent /pulseops-agent
COPY --from=build /pulseops-dlq /pulseops-dlq
ENTRYPOINT ["/pulseops-agent"]
