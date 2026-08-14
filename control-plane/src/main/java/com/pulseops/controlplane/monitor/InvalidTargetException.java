package com.pulseops.controlplane.monitor;

public class InvalidTargetException extends RuntimeException {

    InvalidTargetException(String message) {
        super(message);
    }
}
