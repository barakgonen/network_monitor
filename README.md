# Traffic Interface Tool

A small Java 21 / Maven multi-module toolkit for simulating and observing custom binary
protocol traffic over UDP. It has two runnable apps that talk to each other:

- **traffic-monitor-app** — a Spring Boot service that listens for UDP traffic, decodes it,
  stores recent messages in memory, exposes a REST API, and serves a dark-themed
  live-monitoring web UI. It can also *publish* messages (once or on a repeating schedule),
  and can auto-reply to specific inbound message types via pluggable handlers.
- **traffic-tester-app** — a standalone CLI app that sends synthetic UDP traffic (defined in a
  YAML scenario file) at the monitor, and can optionally listen for messages sent back.

Six Maven modules make up the system, in two decoupled pairs (a generic "core" plus a
schema-aware "application" implementation for each concern):

- **schema-core** / **shared-schemas** — wire-format decode/encode. `schema-core` defines the
  generic `MessageDefinition` contract and header framing; `shared-schemas` implements it once
  per message type (Orange, Banana, TemperatureReading) and is the only module that knows the
  actual binary layouts.
- **handler-core** / **handler-app** — the `onMessageArrived` auto-reply mechanism.
  `handler-core` defines the generic dispatch contract; `handler-app` implements per-message
  reaction logic using the real schema types.
- **traffic-monitor-app** — the Spring Boot service. It depends on all four of the modules
  above for classpath assembly, but its own source never imports a concrete schema or handler
  type — it discovers and loads `MessageDefinition`/`MessageArrivedHandler` implementations by
  fully-qualified class name via reflection (definitions from `config/traffic-tool.yml`,
  handlers via Spring component scanning), keeping the app itself protocol-agnostic.

Two demo protocols currently exist: **Fruit Interface** (Orange, Banana messages) and
**Weather Interface** (TemperatureReading messages).

## Architecture

```
                  UDP :5001 (Fruit)
  traffic-tester ───────────────────► traffic-monitor
       app        UDP :5003 (Weather)      app
                  ───────────────────►   (Spring Boot,
                                          port :8080 HTTP)
                  UDP :7001 (listener)
  traffic-tester ◄───────────────────  traffic-monitor
       app         single/periodic         app
                    publish
```

```
schema-core  ◄── shared-schemas          handler-core  ◄── handler-app
   (generic)      (Fruit/Weather impl)      (generic)       (Fruit/Weather impl)
        ▲                  ▲                     ▲                 ▲
        └──────────────────┴─────────┬───────────┴─────────────────┘
                                      │  (assembly-only; zero source imports)
                             traffic-monitor-app
                                      ▲
                                      │ (depends on shared-schemas directly, uses types directly)
                             traffic-tester-app
```

- `schema-core` and `handler-core` have no dependencies beyond the JDK — pure generic
  contracts, no Fruit/Weather knowledge at all.
- `shared-schemas` implements `schema-core`'s `MessageDefinition` for each real message type
  and still exposes the original `FruitProtocolCodec`/`WeatherProtocolCodec` classes (used
  directly by `traffic-tester-app`, which is *not* schema-agnostic — it's a test tool that's
  allowed to know the wire format).
- `handler-app` implements `handler-core`'s `MessageArrivedHandler` using `shared-schemas`
  types directly — it's the deliberate "application layer where schema types are used."
- `traffic-monitor-app` is the only Spring Boot module; it owns the HTTP API, UI, and ingestion
  pipeline, and stays generic by only ever referencing the `schema-core`/`handler-core`
  interfaces, never the concrete `shared-schemas`/`handler-app` classes.
- `traffic-tester-app` is a plain Java app (no Spring) with its own `main()`.

## Wire protocols

All multi-byte numeric fields are **big-endian**. All strings are **UTF-8, length-prefixed**
with a 4-byte `int32` length. Every message starts with the same 12-byte header:

| Field | Type | Bytes | Notes |
|---|---|---|---|
| `opcode` | int32 | 4 | identifies the message type |
| `sendTimeEpochMillis` | int64 | 8 | sender's timestamp |
| `bodyLength` | int32 | 4 | length of the body that follows |

### Fruit Interface

| Message | Opcode |
|---|---|
| Orange | 1001 |
| Banana | 1002 |

**Orange body:**

| Field | Type |
|---|---|
| `sourceFarmLength` | int32 |
| `sourceFarm` | UTF-8 string |
| `freshness` | byte (1=`very_fresh`, 2=`not_fresh`, 3=`unknown`) |

**Banana body:**

| Field | Type |
|---|---|
| `colorLength` | int32 |
| `color` | UTF-8 string |
| `weight` | float64 |

### Weather Interface

| Message | Opcode |
|---|---|
| TemperatureReading | 2001 |

**TemperatureReading body:**

| Field | Type |
|---|---|
| `stationIdLength` | int32 |
| `stationId` | UTF-8 string |
| `temperatureCelsius` | float64 |
| `condition` | byte (1=`sunny`, 2=`cloudy`, 3=`rainy`, 4=`unknown`) |

Codecs validate header size (≥12 bytes) and that `bodyLength` matches the remaining buffer.

## Module reference

### schema-core (`com.example.schemacore`)

| Class | Purpose |
|---|---|
| `ProtocolHeader` (record) | Generic header: `opcode`, `sendTimeEpochMillis`, `bodyLength` |
| `ProtocolHeaderCodec` | `decodeHeader(ByteBuffer)` / `encodeMessage(opcode, sendTime, body)` — the one place the 12-byte framing is implemented |
| `MessageDefinition` (interface) | `interfaceName()`, `messageType()`, `opcode()`, `decodeBody(ByteBuffer)`, `encodeBody(Map)` — one implementation per real message type |
| `MessageDefinitionRegistry` | Built from a `List<MessageDefinition>`; keyed by opcode and by interfaceName/messageType; `loadFromClassNames(List<String>)` does the reflective `Class.forName` + `newInstance()` loading; fail-fast on duplicate keys |
| `MessageFields` | Static `requireString`/`requireDouble` field-validation helpers shared by all `MessageDefinition` implementations |

### shared-schemas (`com.example.schemas`)

| Package | Classes |
|---|---|
| `com.example.schemas` | `MessageParser`, `MessageSerializer` (interfaces), `ParsedMessage` (record), `ProtocolType` (TCP/UDP enum) — an older, unused scaffold, see [Known gaps](#known-gaps--legacy-code) |
| `com.example.schemas.fruit` | `FruitOpcodes`, `FruitProtocolHeader`, `FruitProtocolCodec` (encode/decode, used directly by `traffic-tester-app`), `FruitFreshness` (enum), `OrangeMessage`, `BananaMessage`, `OrangeMessageDefinition`, `BananaMessageDefinition` (the `schema-core` implementations, loaded reflectively by `traffic-monitor-app`) |
| `com.example.schemas.weather` | `WeatherOpcodes`, `WeatherProtocolHeader`, `WeatherProtocolCodec`, `WeatherCondition` (enum), `TemperatureReadingMessage`, `TemperatureReadingMessageDefinition` |
| `com.example.schemas.demo` | `BananaParser`/`BananaSerializer`, `MangoParser`/`MangoSerializer` — trivial text-based `MessageParser`/`MessageSerializer` demo implementations, unrelated to the binary Fruit/Weather codecs above |

### handler-core (`com.example.handlercore`)

| Class | Purpose |
|---|---|
| `IncomingMessage` (record) | Generic decoded-message view passed to handlers: interface/type, remote host/port, header, body |
| `ReplySender` (interface) | `reply(interfaceName, messageType, fields, host, port)` — how a handler sends a message back out |
| `MessageArrivedHandler` (interface) | `interfaceName()`, `messageType()`, `onMessageArrived(IncomingMessage, ReplySender)` — one implementation per reactive message type |
| `MessageHandlerRegistry` | Keyed by interfaceName/messageType; fail-fast on duplicates |
| `MessageArrivedDispatcher` | Looks up the registry and invokes the matching handler, if any |

### handler-app (`com.example.handlerapp`)

| Package | Classes |
|---|---|
| `com.example.handlerapp.fruit` | `OrangeMessageHandler` (worked example — auto-replies with a Banana when `freshness == not_fresh`), `BananaMessageHandler` (stub) |
| `com.example.handlerapp.weather` | `TemperatureReadingMessageHandler` (stub) |

### traffic-monitor-app (`com.example.monitor`)

| Package | Classes |
|---|---|
| `com.example.monitor` | `TrafficMonitorApplication` — Spring Boot entrypoint; `scanBasePackages` widened to also pick up `com.example.handlerapp` beans |
| `com.example.monitor.config` | `TrafficMonitorProperties` — binds `traffic.*` properties from `application.yml` |
| `com.example.monitor.schema` | `TrafficToolConfig`/`InterfaceConfig`/`MessageConfig` (POJOs for `config/traffic-tool.yml`), `TrafficToolConfigLoader` (SnakeYAML), `MessageSchemaWiringConfig` (`@Bean MessageDefinitionRegistry`, loaded via reflection at startup) |
| `com.example.monitor.model` | `ObservedMessage` — record capturing one decoded/failed inbound packet |
| `com.example.monitor.store` | `RecentMessageStore` — thread-safe bounded `ArrayDeque`, backs `/api/messages/recent` |
| `com.example.monitor.ingestion.udp` | `UdpIngestionRunner` — opens the Fruit and Weather UDP sockets on startup, decodes packets generically via `MessageDefinitionRegistry`, writes to the store, dispatches to `handler-core` |
| `com.example.monitor.publishing` | `MonitorPayloadFactory` (fields map → protocol bytes via `MessageDefinitionRegistry`), `UdpMessagePublisher` (send one datagram), `PeriodicPublisherService` (scheduled repeat send) |
| `com.example.monitor.handler` | `HandlerWiringConfig` — wires the `handler-core` `ReplySender`/`MessageHandlerRegistry`/`MessageArrivedDispatcher` beans on top of `MonitorPayloadFactory`/`UdpMessagePublisher` |
| `com.example.monitor.api` | `MessageController`, `PublishController`, `PeriodicPublishController` + their request/response records |

### traffic-tester-app (`com.example.tester`)

| Package | Classes |
|---|---|
| `com.example.tester` | `TesterMain` — entrypoint; loads scenario, runs send loop, starts listener |
| `com.example.tester.config` | `ScenarioLoader` (SnakeYAML), `TesterScenario`, `PayloadConfig`, `PayloadMode` (enum), `FruitPayloadConfig`, `WeatherPayloadConfig`, `UdpConfig`, `UdpListenerConfig`, `PayloadTargetConfig` |
| `com.example.tester.payload` | `PayloadFactory` — dispatches on `PayloadMode` to the shared-schemas codecs (or raw text/base64/hex) |
| `com.example.tester.udp` | `UdpPublisher` (send), `UdpListener` (background receive + best-effort Fruit/Weather decode + log) |

## traffic-monitor-app details

### Configuration (real)

Two separate config sources, both live:

**`application.yml`** (Spring Boot, bound via `@ConfigurationProperties(prefix = "traffic")`) —
which UDP ports to listen on and the recent-message store size:

```yaml
server:
  port: 8080

traffic:
  udp:
    enabled: true
    fruit-port: 5001
    weather-port: 5003
    buffer-size-bytes: 65507
  store:
    max-size: 500
```

**`config/traffic-tool.yml`** (path from env var `TRAFFIC_TOOL_CONFIG`, default
`config/traffic-tool.yml`) — which `MessageDefinition` classes to load reflectively at startup.
This is what lets `traffic-monitor-app` decode/encode Fruit and Weather messages without ever
importing `com.example.schemas.*`:

```yaml
interfaces:
  - key: fruit
    name: Fruit Interface
    messages:
      - type: Orange
        definitionClass: com.example.schemas.fruit.OrangeMessageDefinition
      - type: Banana
        definitionClass: com.example.schemas.fruit.BananaMessageDefinition
  - key: weather
    name: Weather Interface
    messages:
      - type: TemperatureReading
        definitionClass: com.example.schemas.weather.TemperatureReadingMessageDefinition
```

At startup, `MessageSchemaWiringConfig` flattens every `messages[].definitionClass` across all
interfaces and calls `MessageDefinitionRegistry.loadFromClassNames(...)`, which does
`Class.forName(...).getDeclaredConstructor().newInstance()` for each one. A missing file, bad
class name, or duplicate opcode/interfaceName+messageType fails Spring context startup
immediately (fail-fast, same philosophy as `MessageHandlerRegistry`'s duplicate-handler check).
`UdpIngestionRunner` then decodes any inbound packet generically: read the 12-byte header via
`ProtocolHeaderCodec`, look up the opcode in the registry, call `decodeBody(...)` on whatever
`MessageDefinition` matched — no `"Fruit"`/`"Weather"` branching in the ingestion code at all.
`MonitorPayloadFactory` does the mirror image for outgoing messages (REST publish API and the
auto-reply `ReplySender` below), looking up by `interfaceName`/`messageType` instead of opcode.

### REST API

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET | `/api/messages/recent` | — | Returns the current `ObservedMessage` list (newest first) |
| POST | `/api/publish/udp` | `PublishRequest` | Sends one UDP message |
| POST | `/api/publish/udp/periodic/start` | `PeriodicPublishRequest` | Starts repeating publish |
| POST | `/api/publish/udp/periodic/stop` | — | Stops the periodic publisher |
| GET | `/api/publish/udp/periodic/status` | — | Returns current `PeriodicPublishStatus` |

`PublishRequest`: `interfaceName`, `messageType`, `host`, `port`, `fields` (`Map<String,Object>`).

`PeriodicPublishRequest`: `publishRequest` (a `PublishRequest`), `eventsPerTimeUnit` (int),
`timeUnit` (`SECOND` | `MINUTE` | `HOUR`).

`PeriodicPublishStatus`: `running`, `interfaceName`, `messageType`, `host`, `port`,
`eventsPerTimeUnit`, `timeUnit`, `intervalMillis`, `sentCount`, `lastError`.

Example single-publish request:

```json
POST /api/publish/udp
{
  "interfaceName": "Fruit Interface",
  "messageType": "Banana",
  "host": "localhost",
  "port": 5001,
  "fields": { "color": "yellow", "weight": 142.75 }
}
```

Example periodic-start request:

```json
POST /api/publish/udp/periodic/start
{
  "publishRequest": {
    "interfaceName": "Fruit Interface",
    "messageType": "Banana",
    "host": "traffic-tester-app",
    "port": 7001,
    "fields": { "color": "yellow", "weight": 142.75 }
  },
  "eventsPerTimeUnit": 5,
  "timeUnit": "SECOND"
}
```

### Web UI

Served at `http://localhost:8080`, dark "System Flow Investigator" theme, two tabs:

- **Live Messages** — table of `Observed At | Protocol | Port | Interface | Message Name |
  Message Body | Parse Error`, polling `/api/messages/recent` every ~2s, with a text filter and
  a click-to-inspect JSON detail panel. Raw Base64 is not shown in the table.
- **Sample Publisher** — form to pick interface/message, target host:port, and per-field inputs
  (enum fields render as dropdowns), with "Send Once" plus periodic controls
  (events/time-unit, Start/Stop/Refresh Status).

### Auto-reply message handlers

Every successfully decoded message is also dispatched (asynchronously, off the UDP receive
loop) to a per-message-type `onMessageArrived` hook, so you can react to specific inbound
messages — most commonly by sending a reply. To add one: implement `handler-core`'s
`MessageArrivedHandler` in `handler-app` (`@Component`, `interfaceName()`/`messageType()` pick
which message it reacts to) and use `ReplySender.reply(interfaceName, messageType, fields,
host, port)` to send something back.

Worked example — `handler-app/.../fruit/OrangeMessageHandler.java` replies with a Banana when
an Orange arrives with `freshness == not_fresh`, sent back to the sender's own address:port:

```java
public void onMessageArrived(IncomingMessage message, ReplySender replySender) {
    OrangeMessage orange = new OrangeMessage(
            (String) message.body().get("sourceFarm"),
            FruitFreshness.fromWireName((String) message.body().get("freshness"))
    );

    if (orange.freshness() == FruitFreshness.NOT_FRESH) {
        replySender.reply("Fruit Interface", "Banana",
                Map.of("color", "yellow", "weight", 100.0),
                message.remoteHost(), message.remotePort());
    }
}
```

`BananaMessageHandler` and `TemperatureReadingMessageHandler` are registered stubs (no-op,
with a `// TODO` showing the pattern) — ready to fill in without touching any wiring.

## traffic-tester-app details

### Scenario configuration

Loaded from the path in env var `TRAFFIC_TESTER_CONFIG` (default `./config/tester-scenario.yml`;
in Docker Compose it's `/app/config/tester-scenario.yml`, mounted read-only from `./config`):

```yaml
udp:
  host: 127.0.0.1        # default send target host
  port: 5001              # default send target port

listener:
  enabled: true            # default true
  port: 7001                # default 7001
  durationSeconds: 120       # default 120 — how long to listen after sending
  bufferSizeBytes: 65507     # default 65507

messages:                  # preferred (V2) format — list of messages sent per iteration
  - mode: FRUIT_ORANGE       # FRUIT_ORANGE | FRUIT_BANANA | WEATHER_TEMPERATURE_READING | TEXT | BASE64 | HEX
    target:                  # optional per-message override of udp.host/port
      host: 127.0.0.1
      port: 5001
    fruit:
      sourceFarm: "north-farm-17"   # default "default-farm"
      freshness: "very_fresh"       # default "unknown"
      # color: "yellow"             # Banana only, default "yellow"
      # weight: 142.75              # Banana only, default 120.5
  - mode: WEATHER_TEMPERATURE_READING
    weather:
      stationId: "station-tlv-01"   # default "station-01"
      temperatureCelsius: 28.4      # default 24.5
      condition: "sunny"            # default "sunny"

# payload: {...}          # legacy (V1) format — single message, used only if `messages` is absent

repeat: 1                  # default 1 — number of times to iterate through `messages`
intervalMillis: 1000        # default 1000 — delay between iterations (not after the last one)
```

`TEXT`/`BASE64`/`HEX` modes send raw payloads from the `text`/`base64`/`hex` string fields
instead of an encoded Fruit/Weather message — useful for testing malformed-input handling.

### Behavior

- **Send loop**: for `repeat` iterations, encode and send every entry in `messages` (or the
  single `payload` in V1 configs) to its resolved target (`target.*` override, else top-level
  `udp.*`), sleeping `intervalMillis` between iterations.
- **Listener**: if `listener.enabled`, a background thread binds `listener.port`, logs each
  received packet's source, hex, and UTF-8 text, and attempts to decode it as a Fruit or
  Weather message, logging the parsed fields on success. Runs for `listener.durationSeconds`.

### Standalone run

```bash
mvn -pl traffic-tester-app -am clean package -DskipTests
TRAFFIC_TESTER_CONFIG=config/tester-scenario.yml \
  java -jar traffic-tester-app/target/traffic-tester-app-1.0-SNAPSHOT.jar
```

## Running the project

Start the monitor:

```bash
docker compose up --build traffic-monitor-app
```

Open the UI:

```text
http://localhost:8080
```

In another terminal, run the tester (sends whatever `config/tester-scenario.yml` defines):

```bash
docker compose --profile tester up --build traffic-tester-app
```

Edit `config/tester-scenario.yml` to change what gets sent, or use the monitor's Sample
Publisher UI/API to send ad hoc messages toward the tester's listener
(`traffic-tester-app:7001` inside Docker, `localhost:7001` if both run locally).

Exposed ports: `8080/tcp` (monitor HTTP+UI), `5001/udp` (Fruit), `5003/udp` (Weather),
`7001/udp` (tester listener).

## Development

- Java 21, Maven multi-module build. From the repo root:

  ```bash
  mvn clean package
  ```

  builds all six modules (`schema-core`, `shared-schemas`, `handler-core`, `handler-app`,
  `traffic-monitor-app`, `traffic-tester-app`) in dependency order.
- Run the monitor locally without Docker: `mvn -pl traffic-monitor-app -am spring-boot:run`
  (or run the built jar), using `application.yml` for ports and `config/traffic-tool.yml`
  (via `TRAFFIC_TOOL_CONFIG`, default `config/traffic-tool.yml` relative to the working
  directory) for message definitions — HTTP on `:8080`, UDP on `:5001`/`:5003`.
- Run the tester locally without Docker: see [Standalone run](#standalone-run) above.

## Known gaps / legacy code

- **TCP is not implemented.** `ProtocolType.TCP` exists in `shared-schemas`, but
  `UdpIngestionRunner` only opens UDP sockets — there is no TCP listener anywhere in the code.
- **`com.example.schemas.{MessageParser,MessageSerializer,ParsedMessage}` and
  `shared-schemas.demo`** (`BananaParser`/`Serializer`, `MangoParser`/`Serializer`) are an
  older, unused generic-parsing scaffold — superseded by `schema-core`'s `MessageDefinition`
  (which is the one actually wired up via `config/traffic-tool.yml` and reflection). Nothing
  references these anymore; they're a candidate for deletion, not a live mechanism. Don't
  confuse the demo `BananaParser`/`BananaSerializer` with the real binary `BananaMessage`/
  `BananaMessageDefinition` used by `FruitProtocolCodec`.
