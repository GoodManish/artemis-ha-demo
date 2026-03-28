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
│              (ProducerService, MessageListener)                   │
│                    (JmsConfig HA Setup)                          │
└─────────────────────────────────────────────────────────────────┘
         │
         │ Automatic HA Failover (TransportConfiguration + ServerLocator)
         │
    ┌────┴────────────────────────────────────┬───────────────────┐
    │                                         │                   │
    ▼                                         ▼                   ▼
┌──────────────────────┐          ┌──────────────────────┐
│   LIVE BROKER        │ ◀────┐   │   BACKUP BROKER      │
│ (Primary/Replication)│     │   │  (Replica/Replication)
│ Port: 61616          │Sync │   │ Port: 61617          │
│ Role: PRIMARY        │Via  │   │ Role: REPLICA        │
└──────────────────────┘│    └─────────────────────────┘
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

---

## Project Structure

```
artemis-ha-demo/
├── apache-artemis-2.53.0/              ← Artemis broker binary (unzipped)
│   ├── bin/
│   │   ├── artemis.cmd                 ← Windows CLI tool
│   │   └── artemis                     ← Linux/macOS CLI tool
│   └── ...
│
├── instances/                          ← Created during setup (Step 1)
│   ├── broker-live/                    ← Live broker instance
│   │   ├── bin/
│   │   │   └── artemis.cmd run         ← Run live broker
│   │   ├── etc/
│   │   │   └── broker.xml              ← Primary config (copied)
│   │   └── data/
│   │
│   └── broker-backup/                  ← Backup broker instance
│       ├── bin/
│       │   └── artemis.cmd run         ← Run backup broker
│       ├── etc/
│       │   └── broker.xml              ← Replica config (copied)
│       └── data/
│
├── broker-live/
│   └── broker.xml                      ← Template for live broker config
│
├── broker-backup/
│   └── broker.xml                      ← Template for backup broker config
│
├── src/main/java/com/example/artemisha/
│   ├── ArtemisHaApplication.java       ← Spring Boot entry point
│   ├── config/
│   │   └── JmsConfig.java              ← HA configuration (ServerLocator + TransportConfiguration)
│   ├── service/
│   │   └── ProducerService.java        ← Sends messages to demo.queue
│   └── listener/
│       └── MessageListener.java        ← Consumes messages from demo.queue
│
├── src/main/resources/
│   └── application.properties           ← Broker hosts, ports, credentials
│
├── pom.xml                             ← Maven configuration
└── README.md                           ← This file
```

---

## Getting Started

### Step 1: Create Broker Instances

Open **Command Prompt** (cmd.exe) in the project root folder:

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

### Step 2: Copy Configuration Files

```cmd
copy /Y broker-live\broker.xml   instances\broker-live\etc\broker.xml
copy /Y broker-backup\broker.xml instances\broker-backup\etc\broker.xml
```

### Step 3: Start Live Broker

Open a **new Command Prompt**:

```cmd
instances\broker-live\bin\artemis.cmd run
```

Wait for: `AMQ221007: Server is now live`

### Step 4: Start Backup Broker

Open **another Command Prompt**:

```cmd
instances\broker-backup\bin\artemis.cmd run
```

Wait for: `AMQ222214: Replication: sending ... backup is synchronized`

### Step 5: Run Spring Boot Application

1. Open IntelliJ IDEA
2. **File → Open** → `artemis-ha-demo` folder
3. **Open as Maven Project**
4. Wait for dependencies to download
5. Right-click `ArtemisHaApplication.java` → **Run**

Expected output:
```
SENT     → [#0001] Hello from producer @ 22:48:28
RECEIVED ← [#0001] Hello from producer @ 22:48:28
SENT     → [#0002] Hello from producer @ 22:48:31
RECEIVED ← [#0002] Hello from producer @ 22:48:31
```

### Step 6: Test Failover

1. In the live broker window, press **Ctrl+C**
2. Watch Spring Boot console for: `SEND FAILED (failover in progress?)`
3. In backup window, watch for: `AMQ221007: Server is now live`
4. Messages resume automatically — **no restart needed!**

---

## Understanding the Code

### JmsConfig.java — HA Configuration

The key to HA is using the Java API instead of `failover://` URLs (which Artemis doesn't support):

```java
// Create ServerLocator with HA enabled
ServerLocator locator = ActiveMQClient.createServerLocatorWithHA(
    liveTransport(),           // Live broker host/port
    backupTransport()          // Backup broker host/port
);
locator.setReconnectAttempts(-1);      // Retry forever
locator.setRetryInterval(500);         // 500ms between retries

// Create ConnectionFactory
ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(locator);

// Create JmsTemplate for sending
JmsTemplate jmsTemplate = new JmsTemplate(factory);

// Create listener container for consuming
DefaultJmsListenerContainerFactory listenerFactory = new DefaultJmsListenerContainerFactory();
listenerFactory.setConnectionFactory(factory);
```

**Key insight:** With `ha=true`, the client learns about both brokers and automatically reconnects to backup on failover.

### ProducerService.java — Sends Messages

```java
@Scheduled(fixedDelayString = "${app.producer.interval-ms}")
public void send() {
    String msg = String.format("[#%04d] Hello from producer @ %s",
        counter.incrementAndGet(), LocalTime.now().format(FMT));
    try {
        jmsTemplate.convertAndSend(queueName, msg);
        log.info("SENT     → {}", msg);
    } catch (Exception e) {
        // Brief failures during failover are expected
        log.warn("SEND FAILED (failover in progress?) msg={} error={}", msg, e.getMessage());
    }
}
```

### MessageListener.java — Consumes Messages

```java
@JmsListener(
    destination = "${app.queue.name}",
    containerFactory = "jmsListenerContainerFactory"
)
public void onMessage(String message) {
    log.info("RECEIVED ← {}", message);
}
```

---

## Configuration Guide

### Broker Connection Properties

Edit `src/main/resources/application.properties`:

```properties
# Live broker (primary)
artemis.live.host=localhost
artemis.live.port=61616

# Backup broker (replica)
artemis.backup.host=localhost
artemis.backup.port=61617

# JMS credentials
spring.artemis.user=admin
spring.artemis.password=admin

# Queue and producer settings
app.queue.name=demo.queue
app.producer.interval-ms=8000
```

---

## Adding Users with Specific Roles

### Understanding Roles and Permissions

| Permission | Meaning |
|-----------|---------|
| `send` | Send messages to addresses |
| `consume` | Receive messages from queues |
| `browse` | List messages in queues |
| `createDurableQueue` | Create persistent queues |
| `manage` | Admin operations |

### Step 1: Define Users and Passwords

Edit `instances/broker-live/etc/artemis-users.properties`:

```properties
admin=admin
producer=producerpass123
consumer=consumerpass456
```

Edit `instances/broker-backup/etc/artemis-users.properties` **(same content)**:

```properties
admin=admin
producer=producerpass123
consumer=consumerpass456
```

### Step 2: Assign Users to Roles

Edit `instances/broker-live/etc/artemis-roles.properties`:

```properties
admin=admin
producer=producer_role
consumer=consumer_role
```

Edit `instances/broker-backup/etc/artemis-roles.properties` **(same content)**:

```properties
admin=admin
producer=producer_role
consumer=consumer_role
```

### Step 3: Define Permissions for Each Role

Edit `broker-live/broker.xml` and `broker-backup/broker.xml`.

Replace the `<security-settings>` section:

```xml
<security-settings>
  <!-- Admin: Full permissions -->
  <security-setting match="#">
    <permission type="send"    roles="admin"/>
    <permission type="consume" roles="admin"/>
    <permission type="browse"  roles="admin"/>
    <permission type="manage"  roles="admin"/>
    <permission type="createDurableQueue" roles="admin"/>
  </security-setting>

  <!-- Producer: Send only -->
  <security-setting match="#">
    <permission type="send"    roles="producer_role"/>
  </security-setting>

  <!-- Consumer: Consume and browse -->
  <security-setting match="#">
    <permission type="consume" roles="consumer_role"/>
    <permission type="browse"  roles="consumer_role"/>
  </security-setting>
</security-settings>
```

### Step 4: Address-Level Permissions (Optional)

To restrict permissions per queue:

```xml
<security-settings>
  <!-- demo.queue: producers can send, consumers can consume -->
  <security-setting match="demo.queue">
    <permission type="send"    roles="admin,producer_role"/>
    <permission type="consume" roles="admin,consumer_role"/>
    <permission type="browse"  roles="admin,consumer_role"/>
  </security-setting>

  <!-- admin.queue: admin only -->
  <security-setting match="admin.queue">
    <permission type="send"    roles="admin"/>
    <permission type="consume" roles="admin"/>
    <permission type="browse"  roles="admin"/>
  </security-setting>

  <!-- Deny everything else -->
  <security-setting match="#">
    <!-- Intentionally empty = deny all -->
  </security-setting>
</security-settings>
```

### Step 5: Update application.properties

```properties
spring.artemis.user=producer
spring.artemis.password=producerpass123
```

### Step 6: Restart Brokers

```cmd
copy /Y broker-live\broker.xml instances\broker-live\etc\broker.xml
copy /Y broker-backup\broker.xml instances\broker-backup\etc\broker.xml

REM Restart brokers
instances\broker-live\bin\artemis.cmd run
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `'artemis.cmd' is not recognized` | Run from project root, not from Artemis bin folder |
| `Address already in use: 61616` | Run `netstat -ano \| findstr 61616` and kill that PID |
| Backup never syncs | Start live first, wait for "Server is now live", then start backup |
| `couldn't find JGroups configuration test-jgroups-udp.xml` | Update broker.xml with correct HA config (see broker-live/broker.xml) |
| `Schema failover not found` | Artemis doesn't support `failover://`; use Java API (JmsConfig.java) |
| User auth fails | Ensure users are in both `instances/broker-live/etc/artemis-users.properties` AND `instances/broker-backup/etc/artemis-users.properties` |
| Spring Boot can't find brokers | Check `application.properties` hosts/ports match your brokers |

---

## Version Reference

| Component | Version |
|-----------|---------|
| Java | 17 |
| Spring Boot | 3.2.4 |
| Apache Artemis broker | 2.53.0 |
| artemis-jms-client | 2.53.0 |
| artemis-core-client | 2.53.0 |
| Spring JMS | 6.1.x |
| Jakarta JMS API | 3.1.0 |
