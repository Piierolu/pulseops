package com.pulseops.controlplane.monitor;

import java.util.UUID;

public class MonitorNotFoundException extends RuntimeException {

    MonitorNotFoundException(UUID id) {
        super("Monitor not found: " + id);
    }
}
