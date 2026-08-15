package com.pulseops.controlplane.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitors")
public class Monitor {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "monitor_type", nullable = false, length = 20)
    private MonitorType type;

    @Column(name = "target_url", length = 2048)
    private String targetUrl;

    @Column(name = "target_host", length = 253)
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "dns_record_type", length = 10)
    private String dnsRecordType;

    @Column(name = "expected_value", length = 2048)
    private String expectedValue;

    @Column(name = "tls_expiry_warning_days")
    private Integer tlsExpiryWarningDays;

    @Column(name = "frequency_seconds", nullable = false)
    private int frequencySeconds;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "expected_status")
    private Integer expectedStatus;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Monitor() {
    }

    Monitor(
            UUID id,
            UUID projectId,
            String name,
            MonitorType type,
            String targetUrl,
            String targetHost,
            Integer targetPort,
            String dnsRecordType,
            String expectedValue,
            Integer tlsExpiryWarningDays,
            int frequencySeconds,
            int timeoutMs,
            Integer expectedStatus,
            boolean enabled,
            Instant createdAt
    ) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.type = type;
        this.targetUrl = targetUrl;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.dnsRecordType = dnsRecordType;
        this.expectedValue = expectedValue;
        this.tlsExpiryWarningDays = tlsExpiryWarningDays;
        this.frequencySeconds = frequencySeconds;
        this.timeoutMs = timeoutMs;
        this.expectedStatus = expectedStatus;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public MonitorType getType() {
        return type;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public Integer getTargetPort() {
        return targetPort;
    }

    public String getDnsRecordType() {
        return dnsRecordType;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public Integer getTlsExpiryWarningDays() {
        return tlsExpiryWarningDays;
    }

    public int getFrequencySeconds() {
        return frequencySeconds;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public Integer getExpectedStatus() {
        return expectedStatus;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    void archive(Instant now) {
        enabled = false;
        archivedAt = now;
        updatedAt = now;
    }
}
