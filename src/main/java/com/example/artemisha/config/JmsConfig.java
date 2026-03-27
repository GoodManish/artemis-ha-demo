package com.example.artemisha.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

/**
 * JMS wiring for Artemis HA.
 *
 * Why we build the ConnectionFactory manually (not relying on auto-config):
 * Spring Boot's auto-config creates a CachingConnectionFactory wrapping
 * the Artemis one, which can hide the failover:// URL parameters.
 * Building it explicitly ensures every HA parameter is honoured.
 */
@EnableJms
@Configuration
public class JmsConfig {

    static {
        // Ensure Artemis transport schemas are registered
        System.setProperty("org.apache.activemq.artemis.classLoader", "true");
    }

    @Value("${spring.artemis.broker-url}")
    private String brokerUrl;

    @Value("${spring.artemis.user}")
    private String user;

    @Value("${spring.artemis.password}")
    private String password;

    /**
     * Core connection factory.
     * The failover:// URL is parsed by the Artemis Netty transport layer.
     * ha=true makes the client prefer the active (live) node.
     */
    @Bean
    public ActiveMQConnectionFactory connectionFactory() {
        try {
            // Create connection factory - this will attempt to parse the brokerUrl
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            factory.setUser(user);
            factory.setPassword(password);
            factory.setRetryInterval(1000L);
            factory.setReconnectAttempts(-1);  // Retry forever
            factory.setRetryIntervalMultiplier(1.0);
            return factory;
        } catch (Exception e) {
            System.err.println("Failed to create ActiveMQConnectionFactory: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create ActiveMQConnectionFactory with URL: " + brokerUrl, e);
        }
    }

    /**
     * JmsTemplate — used by ProducerService.
     *
     * deliveryPersistent=true → JMS PERSISTENT delivery mode.
     * Messages are written to the broker journal before send() returns,
     * so they survive failover with zero loss.
     */
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setDeliveryPersistent(true);   // MUST be true for HA
        template.setExplicitQosEnabled(true);   // required to honour deliveryPersistent
        return template;
    }

    /**
     * Listener container factory — used by @JmsListener in MessageListener.
     *
     * sessionTransacted=true → if the broker dies after deliver but before ack,
     *   the session rolls back and the message is redelivered after reconnect.
     *
     * reconnectDelay=1000 → wait 1 second before each reconnect attempt.
     *
     * concurrency="1-1" → single consumer thread (sufficient for this demo).
     *   Use "3-10" in production.
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true);
//        factory.setReconnectDelay(1000L);
        factory.setConcurrency("1-1");
        return factory;
    }
}
