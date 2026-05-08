# Traffic Interface Tool - Reflection Clean Baseline

This clean baseline removes the previous hardcoded demo protocols and keeps the generic reflection-based monitoring infrastructure.

## Modules

```text
shared-schemas
traffic-monitor-app
traffic-tester-app
```

## traffic-monitor-app

Spring Boot backend + static UI.

Kept features:

```text
Reflection-based UDP listeners
Opcode-routed message parsing
Dynamic backend-driven Interfaces panel
Per-interface start/stop listener control
Per-interface runtime state
Recent traffic indicator
Multi-interface UI filtering
ObservedMessage table
Payload inspector
Backend-formatted observedAtDisplay
```

Removed from this clean baseline:

```text
Hardcoded Fruit protocol
Hardcoded Weather protocol
Static monitor publisher
Static periodic publisher
Demo reflection message classes
```

## Reflection interface configuration

Configure real interfaces in:

```text
traffic-monitor-app/src/main/resources/application.yml
```

Example:

```yaml
traffic:
  reflection-interfaces:
    - name: Rada Interface
      enabled: true
      protocol: UDP
      port: 5050
      byte-order: BIG_ENDIAN
      header-type: com.example.schemas.rada.struct.RadaHeader
      opcode-field-name: msgType
      supported-messages:
        "4444":
          message-class: com.example.schemas.rada.messages.RadaExtendedStatus
          display-name: RadaExtendedStatus
```

Runtime flow:

```text
UDP packet arrives on configured port
→ calculate fixed header size from header-type
→ slice header bytes
→ parse header
→ read opcode-field-name
→ route to supported-messages[opcode]
→ parse exact message class
→ validate full ByteBuffer consumption
→ expose parsed body to UI
```

## API

```http
GET  /api/messages/recent
GET  /api/interfaces
POST /api/interfaces/{interfaceName}/start
POST /api/interfaces/{interfaceName}/stop
```

## Observed time display

Backend sends:

```json
{
  "observedAt": "2026-05-08T12:06:19.123Z",
  "observedAtDisplay": "08/05/2026 - 15:06:19.123"
}
```

The UI displays `observedAtDisplay` directly.

Timezone:

```text
Asia/Jerusalem
```

## traffic-tester-app

Generic UDP tester.

Supported payload modes:

```text
TEXT
BASE64
HEX
```

It still includes a UDP listener for receiving responses.

## Run

```bash
docker compose up --build traffic-monitor-app
```

Optional tester:

```bash
docker compose --profile tester up --build traffic-tester-app
```

## Java

```text
Java 17
```

## Tester dependency baseline

`traffic-tester-app` includes:

```xml
<dependency>
  <groupId>org.instancio</groupId>
  <artifactId>instancio-junit</artifactId>
  <version>2.9.0</version>
</dependency>
```
