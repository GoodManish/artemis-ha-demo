package com.example.artemisha.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends a numbered message to demo.queue every few seconds.
 *
 * To test failover:
 *   1. Watch SENT logs flowing normally
 *   2. Kill the live broker (Ctrl+C in its terminal)
 *   3. You'll see a brief SEND FAILED warning (< 5s typically)
 *   4. The backup promotes and messages resume — no restart needed
 */
@Service
public class ProducerService {

    private static final Logger log = LoggerFactory.getLogger(ProducerService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JmsTemplate jmsTemplate;
    private final String queueName;
    private final AtomicInteger counter = new AtomicInteger(0);

    public ProducerService(
            JmsTemplate jmsTemplate,
            @Value("${app.queue.name}") String queueName) {
        this.jmsTemplate = jmsTemplate;
        this.queueName = queueName;
    }

    @Scheduled(fixedDelayString = "${app.producer.interval-ms}")
    public void send() {
        String msg = String.format("[#%04d] Hello from producer @ %s",
                counter.incrementAndGet(), LocalTime.now().format(FMT));
        try {
            jmsTemplate.convertAndSend(queueName, msg);
            log.info("SENT     → {}", msg);
        } catch (Exception e) {
            // Expected briefly during failover window while backup promotes.
            // The next scheduled send will succeed once backup is live.
            log.warn("SEND FAILED (failover in progress?) msg={} error={}", msg, e.getMessage());
        }
    }
}
