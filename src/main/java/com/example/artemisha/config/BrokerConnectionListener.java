package com.example.artemisha.config;

import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugged into the ConnectionFactory via a FailoverEventListener.
 * Logs which broker (live or backup) the client is currently connected to.
 */
public class BrokerConnectionListener implements SessionFailureListener {

    private static final Logger log = LoggerFactory.getLogger(BrokerConnectionListener.class);

    @Override
    public void connectionFailed(ActiveMQException exception, boolean failedOver) {
        if (failedOver) {
            log.warn("⚡ FAILOVER TRIGGERED — switching broker. Cause: {}", exception.getMessage());
        } else {
            log.error("✗ CONNECTION FAILED (no failover). Cause: {}", exception.getMessage());
        }
    }

    @Override
    public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
        connectionFailed(exception, failedOver);
    }

    @Override
    public void beforeReconnect(ActiveMQException exception) {
        log.warn("↻ RECONNECTING... Cause: {}", exception.getMessage());
    }
}