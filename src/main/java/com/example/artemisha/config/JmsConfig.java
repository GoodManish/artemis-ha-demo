package com.example.artemisha.config;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.apache.activemq.artemis.api.core.client.ClusterTopologyListener;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.slf4j.LoggerFactory;
import java.util.Map;

import java.util.HashMap;

/**
 * JMS wiring for Artemis HA — Live + Backup replication.
 *
 * WHY NOT failover:// URL?
 *   failover:// is an ActiveMQ Classic (5.x) transport scheme.
 *   Artemis does NOT support it — passing it to ActiveMQConnectionFactory
 *   throws "Schema failover not found".
 *
 * THE CORRECT ARTEMIS WAY:
 *   HA failover is configured via the Java API:
 *     1. TransportConfiguration  — describes each broker host/port
 *     2. ServerLocator            — built with ha=true + both TransportConfigs
 *     3. ActiveMQConnectionFactory(ServerLocator) — wraps the locator
 *
 *   With ha=true the client connects to the live broker, learns the full
 *   cluster topology (including backup), and automatically reconnects
 *   to the backup on failover — no URL scheme needed.
 */
@EnableJms
@Configuration
public class JmsConfig {

    @Value("${artemis.live.host:localhost}")
    private String liveHost;

    @Value("${artemis.live.port:61616}")
    private int livePort;

    @Value("${artemis.backup.host:localhost}")
    private String backupHost;

    @Value("${artemis.backup.port:61617}")
    private int backupPort;

    @Value("${spring.artemis.user:admin}")
    private String user;

    @Value("${spring.artemis.password:admin}")
    private String password;

    private TransportConfiguration liveTransport() {
        Map<String, Object> params = new HashMap<>();
        params.put(TransportConstants.HOST_PROP_NAME, liveHost);
        params.put(TransportConstants.PORT_PROP_NAME, livePort);
        return new TransportConfiguration(NettyConnectorFactory.class.getName(), params);
    }

    private TransportConfiguration backupTransport() {
        Map<String, Object> params = new HashMap<>();
        params.put(TransportConstants.HOST_PROP_NAME, backupHost);
        params.put(TransportConstants.PORT_PROP_NAME, backupPort);
        return new TransportConfiguration(NettyConnectorFactory.class.getName(), params);
    }

    /**
     * ServerLocator with HA enabled.
     * reconnectAttempts=-1 and initialConnectAttempts=-1 mean retry forever.
     */
    @Bean(destroyMethod = "close")
    public ServerLocator serverLocator() throws Exception {
        ServerLocator locator = ActiveMQClient.createServerLocatorWithHA(
                liveTransport(),
                backupTransport()
        );
        locator.setReconnectAttempts(-1);
        locator.setInitialConnectAttempts(-1);
        locator.setRetryInterval(500);
        locator.setRetryIntervalMultiplier(1.0);

        // ClusterTopologyListener — fires on connect and every failover
        // TransportConfiguration.getParams() gives you the host/port map
        locator.addClusterTopologyListener(new ClusterTopologyListener() {
            private static final Logger log = LoggerFactory.getLogger("BrokerTopology");

            @Override
            public void nodeUP(TopologyMember member, boolean last) {
                String primary = formatTransport(member.getPrimary());
                String backup  = formatTransport(member.getBackup());
                log.info("NODE UP  >> primary={} backup={} nodeId={}",
                        primary, backup, member.getNodeId());
            }

            @Override
            public void nodeDown(long eventUID, String nodeID) {
                log.warn("NODE DOWN >> nodeId={}", nodeID);
            }

            // TransportConfiguration params are a Map<String,Object>
            // Keys are TransportConstants.HOST_PROP_NAME and PORT_PROP_NAME
            private String formatTransport(TransportConfiguration tc) {
                if (tc == null) return "none";
                Map<String, Object> p = tc.getParams();
                return p.get(TransportConstants.HOST_PROP_NAME)
                        + ":" + p.get(TransportConstants.PORT_PROP_NAME);
            }
        });

        return locator;
    }

    @Bean(destroyMethod = "close")
    public ActiveMQConnectionFactory connectionFactory(ServerLocator serverLocator) throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(serverLocator);
        factory.setUser(user);
        factory.setPassword(password);
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ActiveMQConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setDeliveryPersistent(true);
        template.setExplicitQosEnabled(true);
        return template;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ActiveMQConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true);
//        factory.setReconnectDelay(1000L);
        factory.setRecoveryInterval(1000L);
        factory.setConcurrency("1-1");
        return factory;
    }
}
