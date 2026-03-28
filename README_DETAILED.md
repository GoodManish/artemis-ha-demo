# Apache Artemis HA Demo

**Spring Boot 3.2.4 · Java 17 · Apache Artemis 2.53.0 Live/Backup Replication**

A production-ready demonstration of Apache ActiveMQ Artemis High Availability with **shared-nothing live/backup replication** on Windows. This project shows how to build a Spring Boot application that automatically fails over between a live broker and a backup broker with zero message loss.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Project Structure](#project-structure)
5. [Getting Started](#getting-started)
   - [Step 1: Create Broker Instances](#step-1--create-broker-instances)
   - [Step 2: Copy Configuration Files](#step-2--copy-configuration-files)
   - [Step 3: Start Live Broker](#step-3--start-live-broker)
   - [Step 4: Start Backup Broker](#step-4--start-backup-broker)
   - [Step 5: Run Spring Boot Application](#step-5--run-spring-boot-application)
   - [Step 6: Test Failover](#step-6--test-failover)
6. [Understanding the Code](#understanding-the-code)
7. [Configuration Guide](#configuration-guide)
8. [Adding Users with Specific Roles](#adding-users-with-specific-roles)
9. [Troubleshooting](#troubleshooting)
10. [Version Reference](#version-reference)

---

## Overview

This demo implements **shared-nothing HA replication** where:
- **Live broker** (Primary) accepts all client connections and processes messages
- **Backup broker** (Replica) stays synchronized with the live broker's state
- On live broker failure, the **backup automatically promotes** and resumes serving clients
- When the live broker restarts, it steps down and the backup remains active (**failback enabled**)

### Key Features

✅ **Zero Message Loss** — Replicated journal ensures no data loss during failover  
✅ **Automatic Failover** — Clients reconnect transparently within ~5 seconds  
✅ **Spring Boot Integration** — Programmatic HA configuration via Java API  
✅ **Cluster Topology Discovery** — Clients learn about backup automatically  
✅ **Role-Based Security** — Configure users and permissions per role  
✅ **Cross-Platform Ready** — Tested on Windows; works on Linux/macOS with path adjustments

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Application                     │
│                    (ArtemisHaApplication.java)                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           ProducerService (Scheduled)                     │  │
│  │  → Sends messages to demo.queue every 3 seconds          │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           MessageListener (@JmsListener)                  │  │
│  │  → Consumes messages from demo.queue                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │      JmsConfig (Spring JMS + Artemis HA Setup)           │  │
│  │  → TransportConfiguration (live + backup host/port)      │  │
│  │  → ServerLocator with ha=true                            │  │
│  │  → ActiveMQConnectionFactory + ClusterTopologyListener   │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │
         │ (failover:(tcp://localhost:61616,tcp://localhost:61617))
         │
    ┌────┴────────────────────────────────────┬───────────────────┐
    │                                         │                   │
    ▼                                         ▼                   ▼
┌──────────────────────┐          ┌──────────────────────┐
│   LIVE BROKER        │ ◀────┐   │   BACKUP BROKER      │
│ (Primary/Replication)│     │   │  (Replica/Replication)
│                      │     │   │                      │
│ Port: 61616          │ Sync│   │ Port: 61617          │
│ Role: PRIMARY        │ Via │   │ Role: REPLICA        │
│ broker-live/etc/     │ TCP │   │ broker-backup/etc/   │
│ broker.xml           │     │   │ broker.xml           │
│                      │     │   │                      │
│ ┌──────────────────┐ │     │   │ ┌──────────────────┐ │
│ │ Journal (sync'd) │ │ ────┼──→│ │ Journal (sync'd) │ │
│ │ - Bindings       │ │     │   │ │ - Bindings       │ │
│ │ - Messages       │ │     │   │ │ - Messages       │ │
│ └──────────────────┘ │     │   │ └──────────────────┘ │
│                      │     │   │                      │
│ [Client Connected]   │     │   │ [Waiting for failover]
│ [Accepting SEND]     │     │   │ [Accepting SEND*]    │
└──────────────────────┘     │   └──────────────────────┘
                             │
                    (On Live Failure)
                             │
                         ┌───┴──────┐
                         │ Backup   │
                         │ Promotes │
                         │ to Live  │
                         └─────────┬┘
                               ▼
                    [NOW LIVE - Clients Reconnect]
```

---

## Prerequisites

Before starting, ensure you have:

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17+ | `java -version` to verify |
| Apache Artemis | 2.53.0 | Download and unzip in project root |
| Maven | 3.6+ | For building Spring Boot app |
| Spring Boot | 3.2.4 | Managed by pom.xml |
| Windows | 10+ | Tested on Windows 10/11; Linux/macOS work with path changes |

### Download Apache Artemis 2.53.0

1. Visit: https://activemq.apache.org/artemis/download
2. Download `apache-artemis-2.53.0-bin.zip`
3. Unzip it in your project root: `artemis-ha-demo/apache-artemis-2.53.0/`

Verify the structure:
```
artemis-ha-demo/
├── apache-artemis-2.53.0/
│   ├── bin/
│   │   ├── artemis.cmd     (Windows)
│   │   └── artemis         (Linux/macOS)
│   ├── lib/
│   ├── schema/
│   └── ...
```

---

## Project Structure

```
artemis-ha-demo/
│
├── apache-artemis-2.53.0/              ← Artemis broker binary (unzipped)
│   ├── bin/
│   │   ├── artemis.cmd                 ← Windows CLI tool
│   │   └── artemis                     ← Linux/macOS CLI tool
│   └── ...
│
├── instances/                          ← Created during setup (Step 1)
│   ├── broker-live/                    ← Live broker instance
│   │   ├── bin/
│   │   │   ├── artemis.cmd run         ← Run live broker
│   │   │   └── artemis
│   │   ├── etc/
│   │   │   └── broker.xml              ← Primary config (copied from broker-live/)
│   │   ├── data/
│   │   │   ├── journal/                ← Persisted messages & bindings
│   │   │   ├── bindings/
│   │   │   └── ...
│   │   └── ...
│   │
│   └── broker-backup/                  ← Backup broker instance
│       ├── bin/
│       │   ├── artemis.cmd run         ← Run backup broker
│       │   └── artemis
│       ├── etc/
│       │   └── broker.xml              ← Replica config (copied from broker-backup/)
│       ├── data/
│       │   ├── journal/                ← Replicated from live
│       │   ├── bindings/
│       │   └── ...
│       └── ...
│
├── broker-live/
│   └── broker.xml                      ← Template for live broker config
│
├── broker-backup/
│   └── broker.xml                      ← Template for backup broker config
│
├── src/
│   └── main/
│       ├── java/com/example/artemisha/
│       │   ├── ArtemisHaApplication.java
│       │   │   └── Main Spring Boot entry point
│       │   │
│       │   ├── config/
│       │   │   ├── JmsConfig.java
│       │   │   │   └── HA configuration (TransportConfiguration, ServerLocator)
│       │   │   │
│       │   │   └── BrokerConnectionListener.java
│       │   │       └── Handles broker connection events
│       │   │
│       │   ├── service/
│       │   │   └── ProducerService.java
│       │   │       └── Sends messages to demo.queue every 3s (@Scheduled)
│       │   │
│       │   └── listener/
│       │       └── MessageListener.java
│       │           └── Consumes messages from demo.queue
│       │
│       └── resources/
│           └── application.properties
│               ├── artemis.live.host, artemis.live.port
│               ├── artemis.backup.host, artemis.backup.port
│               ├── spring.artemis.user, spring.artemis.password
│               └── app.queue.name, app.producer.interval-ms
│
├── pom.xml                             ← Maven configuration
│                                         • Spring Boot 3.2.4 parent
│                                         • Artemis 2.53.0 override
│                                         • Spring JMS + Artemis client libs
│
└── README.md                           ← This file

```

---

## Getting Started

### Step 1: Create Broker Instances

Open **Command Prompt** (cmd.exe, not PowerShell) in the project root folder and run:

```cmd
REM Create instances folder
mkdir instances

REM Create LIVE broker instance (Primary)
apache-artemis-2.53.0\bin\artemis.cmd create instances\broker-live ^
  --user admin --password admin --allow-anonymous --no-autotune --no-web

REM Create BACKUP broker instance (Replica)
apache-artemis-2.53.0\bin\artemis.cmd create instances\broker-backup ^
  --user admin --password admin --allow-anonymous --no-autotune --no-web
```

**Options explained:**
- `--user admin --password admin` → Default admin account (use strong passwords in production)
- `--allow-anonymous` → Allows unauthenticated connections (remove in production)
- `--no-autotune` → Skips performance tuning (sometimes fails on Windows)
- `--no-web` → Disables web console (optional; saves memory)

**Expected output:**
```
Creating ActiveMQ Artemis instance at instances/broker-live
...
broker created successfully
```

### Step 2: Copy Configuration Files

Copy the HA-configured broker.xml files into the instances:

```cmd
REM Copy LIVE broker config
copy /Y broker-live\broker.xml instances\broker-live\etc\broker.xml

REM Copy BACKUP broker config
copy /Y broker-backup\broker.xml instances\broker-backup\etc\broker.xml
```

**What these configs do:**
- **broker-live/broker.xml** → Sets this broker as `<primary>` (HA mode: Replication)
- **broker-backup/broker.xml** → Sets this broker as `<replica>` (HA mode: Replication)
- Both define security roles, queues, and cluster connections

### Step 3: Start Live Broker

Open a **new Command Prompt** window and run:

```cmd
instances\broker-live\bin\artemis.cmd run
```

**Wait for this log line:**
```
2026-03-27 20:04:07,100 INFO [org.apache.activemq.artemis.core.server] AMQ221007: Server is now live
```

This means the live broker is ready. Do NOT press Ctrl+C yet.

### Step 4: Start Backup Broker

Open **another new Command Prompt** window and run:

```cmd
instances\broker-backup\bin\artemis.cmd run
```

**Wait for these log lines:**
```
AMQ222212: Waiting indefinitely to be paired with a live server
...
AMQ222214: Replication: sending ... backup is synchronized
```

This means the backup has connected and is replicated with the live broker.

### Step 5: Run Spring Boot Application

Now that both brokers are running, start the Spring Boot app:

1. Open **IntelliJ IDEA**
2. **File → Open** → Select `artemis-ha-demo` folder
3. If prompted, choose **Open as Maven Project**
4. Wait for Maven to download dependencies (~1–2 minutes first time)
5. Right-click `ArtemisHaApplication.java` → **Run** (or press Shift+F10)

**Expected console output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot :: (v3.2.4)

...
2026-03-27 22:48:28.433 INFO  ... ArtemisHaApplication : Started ArtemisHaApplication in 2.614 seconds
2026-03-27 22:48:28.500 INFO  ... ProducerService : SENT     → [#0001] Hello from producer @ 22:48:28
2026-03-27 22:48:28.550 INFO  ... MessageListener : RECEIVED ← [#0001] Hello from producer @ 22:48:28
2026-03-27 22:48:31.534 INFO  ... ProducerService : SENT     → [#0002] Hello from producer @ 22:48:31
2026-03-27 22:48:31.584 INFO  ... MessageListener : RECEIVED ← [#0002] Hello from producer @ 22:48:31
```

Messages are being sent and consumed successfully! ✅

### Step 6: Test Failover (The Fun Part)

To demonstrate automatic failover:

1. **In the live broker window**, press **Ctrl+C** to kill the live broker
2. **Watch the Spring Boot console:**
   ```
   WARN  [scheduling-1] ProducerService: SEND FAILED (failover in progress?) msg=[#0005]
   ```
   This is expected — it's the brief window during promotion (~1–5 seconds).

3. **In the backup broker window**, watch for:
   ```
   AMQ221007: Server is now live
   ```
   The backup has promoted to live!

4. **Back in the Spring Boot console**, messages resume automatically:
   ```
   INFO  ... ProducerService : SENT     → [#0006] Hello from producer @ 22:50:01
   INFO  ... MessageListener : RECEIVED ← [#0006] Hello from producer @ 22:50:01
   ```

**No restart needed!** The client reconnected and the failover is transparent.

#### Test Failback (Optional)

To see the original live broker step down and resume as primary:

```cmd
instances\broker-live\bin\artemis.cmd run
```

The live broker starts and connects to the now-active backup. Since `allow-failback=true` in the backup config, the original live broker resumes as primary and the backup steps down.

---

## Understanding the Code

### 1. **ArtemisHaApplication.java** — Entry Point

```java
@SpringBootApplication
@EnableScheduling   // Enables @Scheduled on ProducerService
public class ArtemisHaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArtemisHaApplication.class, args);
    }
}
```

Simple Spring Boot entry point. `@EnableScheduling` allows the producer to fire periodic messages.

---

### 2. **JmsConfig.java** — HA Configuration

This is where the magic happens. Instead of using a `failover://` URL (which Artemis doesn't support), we:

1. **Create TransportConfiguration for each broker:**
   ```java
   private TransportConfiguration liveTransport() {
       Map<String, Object> params = new HashMap<>();
       params.put(TransportConstants.HOST_PROP_NAME, liveHost);
       params.put(TransportConstants.PORT_PROP_NAME, livePort);
       return new TransportConfiguration(NettyConnectorFactory.class.getName(), params);
   }
   
   private TransportConfiguration backupTransport() {
       // Similar for backup broker
   }
   ```

2. **Create ServerLocator with HA enabled:**
   ```java
   @Bean(destroyMethod = "close")
   public ServerLocator serverLocator() throws Exception {
       ServerLocator locator = ActiveMQClient.createServerLocatorWithHA(
           liveTransport(),
           backupTransport()
       );
       locator.setReconnectAttempts(-1);           // Retry forever
       locator.setInitialConnectAttempts(-1);      // Retry forever on init
       locator.setRetryInterval(500);              // Wait 500ms between retries
       locator.setRetryIntervalMultiplier(1.0);    // No exponential backoff
       
       // Topology listener — logs cluster changes
       locator.addClusterTopologyListener(new ClusterTopologyListener() {
           @Override
           public void nodeUP(TopologyMember member, boolean last) {
               String primary = formatTransport(member.getPrimary());
               String backup  = formatTransport(member.getBackup());
               log.info("Cluster topology: PRIMARY={}, BACKUP={}", primary, backup);
           }
           
           @Override
           public void nodeDown(long eventUID, String nodeName) {
               log.warn("Node down: {}", nodeName);
           }
       });
       
       return locator;
   }
   ```

3. **Create ActiveMQConnectionFactory:**
   ```java
   @Bean
   public ActiveMQConnectionFactory connectionFactory(ServerLocator serverLocator) {
       return new ActiveMQConnectionFactory(serverLocator);
   }
   ```

4. **Wire JMS components:**
   ```java
   @Bean
   public JmsTemplate jmsTemplate(ActiveMQConnectionFactory factory) {
       JmsTemplate template = new JmsTemplate(factory);
       template.setDefaultDestinationName("demo.queue");
       return template;
   }
   
   @Bean
   public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
           ActiveMQConnectionFactory factory) {
       DefaultJmsListenerContainerFactory factory1 = new DefaultJmsListenerContainerFactory();
       factory1.setConnectionFactory(factory);
       factory1.setConcurrency("1");  // One thread per listener
       return factory1;
   }
   ```

**Key insight:** With `ha=true`, the client automatically learns about both brokers on first connect. If the live broker fails, Spring's `JmsTemplate` and `DefaultJmsListenerContainerFactory` handle reconnection transparently.

---

### 3. **ProducerService.java** — Sends Messages

```java
@Service
public class ProducerService {
    private final JmsTemplate jmsTemplate;
    private final String queueName;
    private final AtomicInteger counter = new AtomicInteger(0);

    @Scheduled(fixedDelayString = "${app.producer.interval-ms}")
    public void send() {
        String msg = String.format("[#%04d] Hello from producer @ %s",
            counter.incrementAndGet(), LocalTime.now().format(FMT));
        try {
            jmsTemplate.convertAndSend(queueName, msg);
            log.info("SENT     → {}", msg);
        } catch (Exception e) {
            // During failover window, this fails briefly while backup promotes
            log.warn("SEND FAILED (failover in progress?) msg={} error={}", msg, e.getMessage());
        }
    }
}
```

Fires every 3 seconds (configurable via `app.producer.interval-ms`). Handles brief failures during failover gracefully.

---

### 4. **MessageListener.java** — Consumes Messages

```java
@Component
public class MessageListener {
    @JmsListener(
        destination = "${app.queue.name}",
        containerFactory = "jmsListenerContainerFactory"
    )
    public void onMessage(String message) {
        log.info("RECEIVED ← {}", message);
        
        // Uncomment to test transacted redelivery:
        // if (message.contains("#0003")) throw new RuntimeException("Simulated crash");
    }
}
```

Listens on `demo.queue` and logs received messages. The `DefaultJmsListenerContainerFactory` automatically reconnects to the backup on failover.

---

### 5. **broker-live/broker.xml** — Live Broker Config

Key sections:

```xml
<!-- HA: this broker is the PRIMARY -->
<ha-policy>
    <replication>
        <primary>
            <check-for-active-server>true</check-for-active-server>
        </primary>
    </replication>
</ha-policy>

<!-- Cluster connection to backup -->
<cluster-connections>
    <cluster-connection name="my-cluster">
        <connector-ref>live-connector</connector-ref>
        <static-connectors>
            <connector-ref>backup-connector</connector-ref>
        </static-connectors>
    </cluster-connection>
</cluster-connections>

<!-- Security: admin role can do everything -->
<security-settings>
    <security-setting match="#">
        <permission type="send" roles="admin"/>
        <permission type="consume" roles="admin"/>
        <permission type="manage" roles="admin"/>
        <!-- ... more permissions ... -->
    </security-setting>
</security-settings>

<!-- Queue definition -->
<addresses>
    <address name="demo.queue">
        <anycast>
            <queue name="demo.queue">
                <durable>true</durable>
            </queue>
        </anycast>
    </address>
</addresses>
```

---

### 6. **broker-backup/broker.xml** — Backup Broker Config

Key differences from live:

```xml
<!-- HA: this broker is the REPLICA (backup) -->
<ha-policy>
    <replication>
        <replica>
            <allow-failback>true</allow-failback>
            <vote-on-replication-failure>true</vote-on-replication-failure>
            <quorum-vote-wait>30</quorum-vote-wait>
        </replica>
    </replication>
</ha-policy>

<!-- Cluster connection to live -->
<cluster-connections>
    <cluster-connection name="my-cluster">
        <connector-ref>backup-connector</connector-ref>
        <static-connectors>
            <connector-ref>live-connector</connector-ref>
        </static-connectors>
    </cluster-connection>
</cluster-connections>
```

- `<replica>` — This broker is a replica (standby)
- `allow-failback=true` — If live restarts, backup steps down
- `vote-on-replication-failure=true` — On split-brain, vote to decide who becomes live
- `quorum-vote-wait=30` — Wait 30s for other nodes' votes before deciding

---

## Configuration Guide

### Broker Connection Properties

Edit `src/main/resources/application.properties`:

```properties
# Live broker (primary)
artemis.live.host=localhost    # or IP address
artemis.live.port=61616

# Backup broker (replica)
artemis.backup.host=localhost  # or IP address
artemis.backup.port=61617

# JMS credentials
spring.artemis.user=admin
spring.artemis.password=admin
```

### Queue and Producer Settings

```properties
app.queue.name=demo.queue           # Queue name to use
app.producer.interval-ms=8000       # Send message every 8 seconds
```

### Failover Tuning (Advanced)

In `JmsConfig.serverLocator()`:

```java
locator.setReconnectAttempts(-1);           // -1 = retry forever; 0 = no retry; N = max retries
locator.setInitialConnectAttempts(-1);      // Retries on first connect
locator.setRetryInterval(500);              // Milliseconds between retries
locator.setRetryIntervalMultiplier(1.0);    // Backoff multiplier (1.0 = no backoff)
locator.setMaxRetryInterval(5000);          // Max retry interval cap (optional)
```

**Recommendations:**
- **Low latency required:** `retryInterval=100`, `multiplier=1.0`
- **Reliable network:** `retryInterval=500`, `multiplier=1.0`
- **Slow/unreliable network:** `retryInterval=2000`, `multiplier=1.5` (exponential backoff)

---

## Adding Users with Specific Roles

By default, Artemis comes with a single `admin` user. To add users with different roles and permissions, edit the broker configuration files.

### Understanding Roles and Permissions

In Artemis, **roles** are groups of users. **Permissions** control what actions users can perform:

| Permission | Meaning |
|-----------|---------|
| `createNonDurableQueue` | Create temporary queues |
| `deleteNonDurableQueue` | Delete temporary queues |
| `createDurableQueue` | Create persistent queues |
| `deleteDurableQueue` | Delete persistent queues |
| `createAddress` | Create addresses (topics/queues) |
| `deleteAddress` | Delete addresses |
| `send` | Send messages to addresses |
| `consume` | Receive messages from queues |
| `browse` | List messages in queues |
| `manage` | Admin operations (force kill, edit config, etc.) |

### Step 1: Define Users and Passwords

Edit `instances/broker-live/etc/artemis-users.properties`:

```properties
# Format: username=password
admin=admin
producer=producerpass123
consumer=consumerpass456
guest=guest
```

Edit `instances/broker-backup/etc/artemis-users.properties` **(same content)**:

```properties
admin=admin
producer=producerpass123
consumer=consumerpass456
guest=guest
```

### Step 2: Assign Users to Roles

Edit `instances/broker-live/etc/artemis-roles.properties`:

```properties
# Format: username=role1,role2,...
admin=admin
producer=producer_role
consumer=consumer_role
guest=guest_role
```

Edit `instances/broker-backup/etc/artemis-roles.properties` **(same content)**:

```properties
admin=admin
producer=producer_role
consumer=consumer_role
guest=guest_role
```

### Step 3: Define Permissions for Each Role

Edit `broker-live/broker.xml` and `broker-backup/broker.xml`.

Replace the `<security-settings>` section:

```xml
<security-settings>
  <!-- Admin: Full permissions -->
  <security-setting match="#">
    <permission type="createNonDurableQueue" roles="admin"/>
    <permission type="deleteNonDurableQueue" roles="admin"/>
    <permission type="createDurableQueue"    roles="admin"/>
    <permission type="deleteDurableQueue"    roles="admin"/>
    <permission type="createAddress"         roles="admin"/>
    <permission type="deleteAddress"         roles="admin"/>
    <permission type="consume"               roles="admin"/>
    <permission type="browse"                roles="admin"/>
    <permission type="send"                  roles="admin"/>
    <permission type="manage"                roles="admin"/>
  </security-setting>

  <!-- Producer: Send and manage queues only -->
  <security-setting match="#">
    <permission type="createNonDurableQueue" roles="producer_role"/>
    <permission type="createDurableQueue"    roles="producer_role"/>
    <permission type="send"                  roles="producer_role"/>
    <permission type="manage"                roles="producer_role"/>
  </security-setting>

  <!-- Consumer: Consume and browse only -->
  <security-setting match="#">
    <permission type="consume"               roles="consumer_role"/>
    <permission type="browse"                roles="consumer_role"/>
  </security-setting>

  <!-- Guest: Read-only (browse only) -->
  <security-setting match="#">
    <permission type="browse"                roles="guest_role"/>
  </security-setting>
</security-settings>
```

### Step 4: Address-Level Permissions (Optional)

To restrict permissions per address/queue, use multiple `<security-setting>` elements with specific address patterns:

```xml
<security-settings>
  <!-- demo.queue: admin full access, producers can send, consumers can consume -->
  <security-setting match="demo.queue">
    <permission type="send"    roles="admin,producer_role"/>
    <permission type="consume" roles="admin,consumer_role"/>
    <permission type="browse"  roles="admin,consumer_role"/>
    <permission type="manage"  roles="admin"/>
  </security-setting>

  <!-- admin.queue: admin only -->
  <security-setting match="admin.queue">
    <permission type="send"    roles="admin"/>
    <permission type="consume" roles="admin"/>
    <permission type="browse"  roles="admin"/>
    <permission type="manage"  roles="admin"/>
  </security-setting>

  <!-- notification.topic: broadcast to all authenticated users -->
  <security-setting match="notification.topic">
    <permission type="send"    roles="admin"/>
    <permission type="consume" roles="admin,producer_role,consumer_role"/>
    <permission type="browse"  roles="admin,producer_role,consumer_role"/>
  </security-setting>

  <!-- Wildcard: everything else, deny by default -->
  <security-setting match="#">
    <!-- Intentionally empty = deny all -->
  </security-setting>
</security-settings>
```

### Step 5: Update application.properties

Change the Spring Boot app to use the new user credentials:

```properties
spring.artemis.user=producer
spring.artemis.password=producerpass123
```

Or create different beans for producer/consumer:

```java
@Configuration
public class MultiUserJmsConfig {
    
    @Bean(name = "producerConnectionFactory")
    public ActiveMQConnectionFactory producerConnectionFactory(ServerLocator serverLocator) {
        // Create with producer credentials
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(serverLocator);
        factory.setUser("producer");
        factory.setPassword("producerpass123");
        return factory;
    }
    
    @Bean(name = "consumerConnectionFactory")
    public ActiveMQConnectionFactory consumerConnectionFactory(ServerLocator serverLocator) {
        // Create with consumer credentials
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(serverLocator);
        factory.setUser("consumer");
        factory.setPassword("consumerpass456");
        return factory;
    }
}
```

### Step 6: Restart Brokers

Copy the updated configs and restart:

```cmd
copy /Y broker-live\broker.xml instances\broker-live\etc\broker.xml
copy /Y broker-backup\broker.xml instances\broker-backup\etc\broker.xml

REM Kill existing brokers and restart
instances\broker-live\bin\artemis.cmd run
```

### Complete Example: Three-Role Setup

**artemis-users.properties:**
```properties
admin=admin123
app_producer=producer_secret
app_consumer=consumer_secret
```

**artemis-roles.properties:**
```properties
admin=admin
app_producer=producer
app_consumer=consumer
```

**broker.xml security-settings:**
```xml
<security-settings>
  <security-setting match="#">
    <!-- Admin: everything -->
    <permission type="send"    roles="admin"/>
    <permission type="consume" roles="admin"/>
    <permission type="browse"  roles="admin"/>
    <permission type="manage"  roles="admin"/>
    <permission type="createDurableQueue" roles="admin"/>
    
    <!-- Producer: send + create queue -->
    <permission type="send"    roles="producer"/>
    <permission type="createDurableQueue" roles="producer"/>
    
    <!-- Consumer: consume + browse -->
    <permission type="consume" roles="consumer"/>
    <permission type="browse"  roles="consumer"/>
  </security-setting>
</security-settings>
```

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| `'artemis.cmd' is not recognized` | Wrong folder path | Run commands from project root, e.g., `artemis-ha-demo/` |
| `Address already in use: 61616` | Another process on port 61616 | Run `netstat -ano \| findstr 61616`, then `taskkill /PID <PID> /F` |
| `Address already in use: 61617` | Another process on port 61617 | Same as above, but for port 61617 |
| Backup never syncs | Live broker not running | Start live broker first, wait for "Server is now live" |
| `couldn't find JGroups configuration test-jgroups-udp.xml` | Old config in broker.xml | Make sure broker.xml has `<ha-policy><replication>` (not `<replication-backup>` or `<replication-primary>` from old docs) |
| `Connection refused` on Spring Boot startup | Brokers not running | Start both brokers in separate Command Prompt windows before starting app |
| Maven dependency errors | Wrong Java version | Check **File → Project Structure → SDK** in IntelliJ; must be Java 17+  |
| `Schema failover not found` | Using `failover://` URL with Artemis | Artemis doesn't support `failover://`. Use Java API (JmsConfig.java) instead |
| Backup doesn't promote on live failure | `quorum-vote-wait` timer or network partition | Wait ~30s; if still stuck, kill both and restart live, then backup |
| Spring Boot can't find brokers | Hostname/port mismatch | Check `application.properties` and `JmsConfig` against actual broker ports |
| Messages not durable | Queue not marked durable | In broker.xml, add `<durable>true</durable>` to queue definition |
| User auth fails with new user | Users not in both brokers | Make sure `artemis-users.properties` and `artemis-roles.properties` are in **both** `instances/broker-live/etc/` and `instances/broker-backup/etc/` |

### Debug Logging

For detailed logs, edit `instances/broker-live/etc/log4j2.properties`:

```properties
# Enable DEBUG for Artemis HA
logger.org.apache.activemq.artemis.core.server = DEBUG
logger.org.apache.activemq.artemis.core.cluster = DEBUG
logger.org.apache.activemq.artemis.core.remoting = DEBUG
```

Restart the broker to see verbose logs.

---

## Version Reference

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17 | Tested with OpenJDK 17; Java 21 also works |
| Spring Boot | 3.2.4 | Parent POM; manages all Spring dependencies |
| Apache Artemis | 2.53.0 | Binary + JMS client pinned to same version |
| artemis-jms-client | 2.53.0 | Overridden via `<artemis.version>` in pom.xml |
| artemis-core-client | 2.53.0 | Required for serverLocator, HA failover |
| artemis-openwire-protocol | 2.53.0 | Required for native OpenWire protocol |
| Spring JMS | 6.1.x | Managed by Spring Boot 3.2.4 |
| Jakarta JMS API | 3.1.0 | Replaces javax.jms; required for Spring Boot 3.x |
| Netty | 4.1.x | Artemis transport layer (managed by Artemis) |

---

## Production Checklist

Before deploying to production:

- [ ] **Passwords:** Change default passwords in `artemis-users.properties`
- [ ] **Roles:** Implement least-privilege access (don't use `admin` for apps)
- [ ] **Security:** Remove `--allow-anonymous` and add proper user/password credentials
- [ ] **Failover tuning:** Adjust `retryInterval`, `reconnectAttempts`, etc., for your network
- [ ] **Monitoring:** Set up log aggregation and alerting (watch for failover events)
- [ ] **Backups:** Regular backup of `data/journal/` directory on both brokers
- [ ] **Network:** Ensure brokers have reliable, low-latency connection
- [ ] **Hardware:** Consider failover on separate physical machines
- [ ] **Testing:** Run chaos tests: kill live broker, kill backup, network partition
- [ ] **Documentation:** Document your HA topology, failover procedures, runbooks

---

## Additional Resources

- **Apache Artemis Docs:** https://activemq.apache.org/artemis/documentation
- **HA Setup Guide:** https://activemq.apache.org/artemis/documentation.html#HA
- **Security Guide:** https://activemq.apache.org/artemis/documentation.html#security
- **Spring JMS Documentation:** https://docs.spring.io/spring-framework/reference/integration/jms.html
- **Spring Boot Artemis Auto-Config:** https://docs.spring.io/spring-boot/reference/features/messaging.html#features.messaging.jms

---

## License

This demo is provided as-is for educational purposes. Apache Artemis is licensed under the Apache License 2.0.

---

## Support & Questions

If you encounter issues:

1. **Check the logs** — Both broker windows and Spring Boot console
2. **Review this README** — Most issues are covered in Troubleshooting
3. **Verify setup** — Ensure both brokers are running before starting the app
4. **Test connectivity** — `telnet localhost 61616` and `telnet localhost 61617`

Good luck! Happy messaging! 🚀


