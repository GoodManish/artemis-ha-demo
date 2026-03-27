package com.example.artemisha.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Consumes messages from demo.queue.
 *
 * No HA-specific code needed here.
 * The DefaultJmsListenerContainerFactory configured in JmsConfig handles:
 *   - Reconnection to the backup broker after failover
 *   - Transaction rollback + redelivery if this method throws
 */
@Component
public class MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageListener.class);

    @JmsListener(
        destination  = "${app.queue.name}",
        containerFactory = "jmsListenerContainerFactory"
    )
    public void onMessage(String message) {
        log.info("RECEIVED ← {}", message);

        // Uncomment to test transacted redelivery on failure:
        // if (message.contains("#0003")) throw new RuntimeException("Simulated crash");
    }
}
