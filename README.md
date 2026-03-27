# Artemis HA Demo — Windows Setup Guide
## Spring Boot 3.3 · Java 21 · Apache Artemis 2.53.0

---

## Assumed Folder Layout

After you unzip Artemis **inside** the Spring Boot project, your layout will look like this:

```
artemis-ha-demo\                        ← Spring Boot project root (open THIS in IntelliJ)
├── apache-artemis-2.53.0\              ← Artemis binary (unzipped here)
│   ├── bin\
│   │   └── artemis.cmd                 ← Windows CLI tool
│   └── ...
├── broker-live\
│   └── broker.xml                      ← Copy to instances\broker-live\etc\broker.xml
├── broker-backup\
│   └── broker.xml                      ← Copy to instances\broker-backup\etc\broker.xml
├── src\
├── pom.xml
└── README.md
```

> You will create the `instances\` folder in the next steps.

---

## Step 1 — Create the Two Broker Instances

Open **Command Prompt** (not PowerShell) in the project root.

```cmd
REM Create a folder for broker instances inside the project
mkdir instances

REM Create the LIVE broker instance
apache-artemis-2.53.0\bin\artemis.cmd create instances\broker-live ^
  --user admin --password admin --allow-anonymous --no-autotune --no-web

REM Create the BACKUP broker instance
apache-artemis-2.53.0\bin\artemis.cmd create instances\broker-backup ^
  --user admin --password admin --allow-anonymous --no-autotune --no-web
```

> `--no-autotune` skips hardware detection that can fail on some Windows systems.
> `--no-web` disables the web console to keep startup simple.

---

## Step 2 — Copy the broker.xml Files

Still in Command Prompt at the project root:

```cmd
copy /Y broker-live\broker.xml   instances\broker-live\etc\broker.xml
copy /Y broker-backup\broker.xml instances\broker-backup\etc\broker.xml
```

---

## Step 3 — Start the Live Broker

Open a **new** Command Prompt window. Navigate to the project root, then:

```cmd
instances\broker-live\bin\artemis.cmd run
```

Wait until you see this line before moving on:
```
AMQ221007: Server is now live
```

---

## Step 4 — Start the Backup Broker

Open **another** Command Prompt window. Navigate to the project root, then:

```cmd
instances\broker-backup\bin\artemis.cmd run
```

You should see the backup connect and sync:
```
AMQ222212: Waiting indefinitely to be paired with a live server
AMQ222214: Replication: sending AckManager ... backup is synchronized
```

---

## Step 5 — Import and Run in IntelliJ

1. Open IntelliJ IDEA
2. **File → Open** → select the `artemis-ha-demo` folder (the Spring Boot project root)
3. IntelliJ detects `pom.xml` → click **Open as Maven Project** if prompted
4. Wait for Maven to download dependencies (~1–2 min first time)
5. Right-click `ArtemisHaApplication.java` → **Run**

Expected console output:
```
SENT     → [#0001] Hello from producer @ 10:32:01
RECEIVED ← [#0001] Hello from producer @ 10:32:01
SENT     → [#0002] Hello from producer @ 10:32:04
RECEIVED ← [#0002] Hello from producer @ 10:32:04
```

---

## Step 6 — Test Failover (the fun part)

1. With everything running, go to the **live broker** window
2. Press `Ctrl+C` to kill it
3. Watch the Spring Boot console — you'll see briefly:
   ```
   WARN  SEND FAILED (failover in progress?)
   ```
4. Then within ~5 seconds, the backup promotes:
   ```
   AMQ221007: Server is now live   ← in the backup window
   ```
5. Messages resume in Spring Boot automatically — no restart needed

**To test failback:** restart the live broker with `instances\broker-live\bin\artemis.cmd run`.
The backup steps down (`allow-failback=true`) and the original live resumes as primary.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `'artemis.cmd' is not recognized` | Make sure you are running from the project root folder, not from inside the Artemis bin folder |
| `Address already in use: 61616` | Another process is on that port. Run `netstat -ano \| findstr 61616` to find and kill it |
| `Connection refused` on Spring Boot start | Both brokers must be running before starting the app |
| Backup never syncs | Start live broker first, wait for "Server is now live", then start backup |
| Maven dependency errors | Ensure Java 21 is set in IntelliJ: **File → Project Structure → SDK** |
| `AMQ229057: There are no other servers to vote` | Normal for a 2-node cluster — backup will still failover after quorum-vote-wait (30s) |

---

## Version Reference

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.4 |
| Apache Artemis broker | 2.53.0 |
| artemis-jms-client | 2.53.0 (pinned via `<artemis.version>` in pom.xml) |
| artemis-jakarta-client | 2.53.0 |
