# Traffic Interface Tool

A small Java 21 / Maven multi-module toolkit for simulating and observing custom binary
protocol traffic over UDP. It has two runnable apps that talk to each other:

- **traffic-monitor-app** — a Spring Boot service that listens for UDP traffic, decodes it,
  stores recent messages in memory, exposes a REST API, and serves a dark-themed
  live-monitoring web UI. It can also *publish* messages (once or on a repeating schedule),
  and can auto-reply to specific inbound message types via pluggable handlers.
- **traffic-tester-app** — a standalone CLI app that sends synthetic UDP traffic (defined in a
  YAML scenario file) at the monitor, and can optionally listen for messages sent back.

Seven Maven modules make up the system, split so the "engine" is a generic, distributable SDK
and everything Fruit/Weather-specific is a swappable, client-suppliable layer on top:

- **schema-core** / **handler-core** — the generic engine's two contracts, zero Fruit/Weather
  knowledge: `schema-core` defines `MessageDefinition` (wire decode/encode) with zero
  dependencies beyond the JDK, and `handler-core` defines `MessageArrivedHandler<T>` (the
  generic, typed `onMessageArrived` auto-reply dispatch contract), depending on `schema-core`
  only for the `ProtocolMessage` marker type.
- **shared-schemas** / **handler-app** — *our* concrete implementations of those two contracts
  for the Fruit/Weather protocols. Neither is required by the engine — they're supplied by
  whoever assembles a runnable app, exactly like a plugin.
- **traffic-monitor-app-core** — the generic engine itself: ingestion, store, REST API, UI
  resources, publishing, reflection/scan-based wiring. Depends on `schema-core` + `handler-core`
  only — **not** `shared-schemas`, **not** `handler-app`. This is the module a client would
  take a dependency on to build their *own* monitor with their *own* protocol and handlers.
- **traffic-monitor-app** — a thin packaging shell with no source of its own; it's *our*
  reference assembly of the engine. Depends on `traffic-monitor-app-core` + `shared-schemas` +
  `handler-app` (all three assembly-only) and holds the `spring-boot-maven-plugin` config that
  produces the final runnable/deployable jar. A client building their own monitor would write
  their own thin module shaped exactly like this one, swapping in their own schema/handler
  modules (or reusing ours).

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
   schema-core          handler-core          <-- the generic engine's contracts (SDK)
   (generic)             (generic)
        ▲                     ▲
        └──────────┬──────────┘
                    │  (compile dependency; zero schema/handler imports)
         traffic-monitor-app-core             <-- the generic engine itself (distributable)
                    ▲
                    │  (assembly-only; traffic-monitor-app has no src/ of its own)
                    │
   shared-schemas ──┼── handler-app           <-- our concrete plugin implementations
        ▲           │        ▲                    (a client could supply their own instead)
        └───────────┼────────┘
                    traffic-monitor-app        <-- our reference assembly (client pattern)
                    │
                    │  spring-boot-maven-plugin repackage -> the runnable jar
                    ▼
                (Docker image)

  traffic-tester-app ──► shared-schemas (depends on it directly, uses types directly)
```

- `schema-core` and `handler-core` have no dependencies beyond the JDK — pure generic
  contracts, no Fruit/Weather knowledge at all. Together they're the reusable "SDK" a client
  would build against.
- `shared-schemas` implements `schema-core`'s `MessageDefinition` for each real message type
  and still exposes the original `FruitProtocolCodec`/`WeatherProtocolCodec` classes (used
  directly by `traffic-tester-app`, which is *not* schema-agnostic — it's a test tool that's
  allowed to know the wire format).
- `handler-app` implements `handler-core`'s `MessageArrivedHandler` using `shared-schemas`
  types directly — it's the deliberate "application layer where schema types are used."
- `traffic-monitor-app-core` owns the HTTP API, UI, and ingestion pipeline, and stays fully
  generic by only ever referencing the `schema-core`/`handler-core` interfaces — a Maven-level
  guarantee (no `shared-schemas` or `handler-app` dependency at all in its `pom.xml`), not just
  a source-code convention. A client can depend on this module's published jar directly.
- `traffic-monitor-app` is the only module that depends on `traffic-monitor-app-core` +
  `shared-schemas` + `handler-app` together, and the only one that runs
  `spring-boot-maven-plugin`'s `repackage` goal — it's purely an assembly/packaging module (no
  `src/` of its own), and its final fat jar is what the Dockerfile copies. This module is the
  *pattern* a client replicates: their own thin shell depending on `traffic-monitor-app-core` +
  their own schema/handler modules (or ours).
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

### Ping Interface

A minimal extensibility proof: send a Ping, the monitor auto-replies with a Pong. Demonstrates
adding a brand-new interface with zero changes to `schema-core`/`handler-core`/
`traffic-monitor-app-core` — only `shared-schemas` (2 message definitions),
`handler-app` (1 handler), `config/traffic-tool.yml`, and `traffic-tester-app` changed.

| Message | Opcode |
|---|---|
| Ping | 3001 |
| Pong | 3002 |

**Ping/Pong body (identical shape):**

| Field | Type |
|---|---|
| `sequence` | int32 |

`handler-app`'s `PingMessageHandler` replies to every Ping with a Pong echoing the same
`sequence`, sent back to the sender's address:port — see
[Auto-reply message handlers](#auto-reply-message-handlers).

Codecs validate header size (≥12 bytes) and that `bodyLength` matches the remaining buffer.

## Module reference

### schema-core (`com.example.schemacore`)

| Class | Purpose |
|---|---|
| `ProtocolHeader` (record) | Generic header: `opcode`, `sendTimeEpochMillis`, `bodyLength` |
| `ProtocolHeaderCodec` | `decodeHeader(ByteBuffer)` / `encodeMessage(opcode, sendTime, body)` — the one place the 12-byte framing is implemented |
| `ProtocolMessage` (interface) | Empty marker interface — every concrete message record (`OrangeMessage`, `BananaMessage`, ...) implements it, purely so generic code (`ReplySender`) can accept "a real message" without depending on `shared-schemas` |
| `MessageDefinition` (interface) | `interfaceName()`, `messageType()`, `opcode()`, `messageClass()`, `decodeBody(ByteBuffer)` → `Map` (for `ObservedMessage`/UI display), `decodeMessage(ByteBuffer)` → `ProtocolMessage` (for typed handler dispatch), `encodeBody(Map)` (for the REST publish API), `encodeBody(ProtocolMessage)` (for `ReplySender`) — one implementation per real message type |
| `MessageDefinitionRegistry` | Built from a `List<MessageDefinition>`; keyed by opcode, by interfaceName/messageType, **and** by `messageClass()` (so a typed instance can resolve its own definition); `loadFromClassNames(List<String>)` does the reflective `Class.forName` + `newInstance()` loading; fail-fast on duplicate keys in any index |
| `MessageFields` | Static `requireString`/`requireDouble`/`requireInt` field-validation helpers shared by the `Map`-based `encodeBody` implementations |

### shared-schemas (`com.example.schemas`)

| Package | Classes |
|---|---|
| `com.example.schemas` | `MessageParser`, `MessageSerializer` (interfaces), `ParsedMessage` (record), `ProtocolType` (TCP/UDP enum) — an older, unused scaffold, see [Known gaps](#known-gaps--legacy-code) |
| `com.example.schemas.fruit` | `FruitOpcodes`, `FruitProtocolHeader`, `FruitProtocolCodec` (encode/decode, used directly by `traffic-tester-app`), `FruitFreshness` (enum), `OrangeMessage`, `BananaMessage`, `OrangeMessageDefinition`, `BananaMessageDefinition` (the `schema-core` implementations, loaded reflectively by `traffic-monitor-app`) |
| `com.example.schemas.weather` | `WeatherOpcodes`, `WeatherProtocolHeader`, `WeatherProtocolCodec`, `WeatherCondition` (enum), `TemperatureReadingMessage`, `TemperatureReadingMessageDefinition` |
| `com.example.schemas.ping` | `PingOpcodes`, `PingMessage`, `PongMessage`, `PingProtocolCodec` (encode/decode, used directly by `traffic-tester-app`), `PingMessageDefinition`, `PongMessageDefinition` — the extensibility proof interface |
| `com.example.schemas.demo` | `BananaParser`/`BananaSerializer`, `MangoParser`/`MangoSerializer` — trivial text-based `MessageParser`/`MessageSerializer` demo implementations, unrelated to the binary Fruit/Weather codecs above |

### handler-core (`com.example.handlercore`)

Depends on `schema-core` (its only dependency) purely for the `ProtocolMessage` type — still
zero Fruit/Weather knowledge.

| Class | Purpose |
|---|---|
| `DestinationConfig` (record) | `(String host, int port)` — the resolved auto-reply destination for the interface that just fired, handed to the handler so it doesn't have to guess |
| `ReplySender` (interface) | `reply(ProtocolMessage message, String host, int port)` — construct a real typed message instance and send it to an explicit host/port |
| `MessageArrivedHandler<T>` (interface) | Generic over the concrete incoming message type. `interfaceName()`, `messageType()`, `onMessageArrived(T message, ReplySender replySender, DestinationConfig destinationConfig)` — `message` arrives already decoded into its real type (e.g. `OrangeMessage`), no `Map` unpacking needed; `destinationConfig` is `null` if no destination is configured for that interface |
| `MessageHandlerRegistry` | Keyed by interfaceName/messageType; fail-fast on duplicates |
| `MessageArrivedDispatcher` | Looks up the registry and invokes the matching handler, if any |

### handler-app (`com.example.handlerapp`)

| Package | Classes |
|---|---|
| `com.example.handlerapp.fruit` | `OrangeMessageHandler` (worked example — auto-replies with a Banana when `freshness == not_fresh`), `BananaMessageHandler` (stub) |
| `com.example.handlerapp.weather` | `TemperatureReadingMessageHandler` (stub) |
| `com.example.handlerapp.ping` | `PingMessageHandler` — always auto-replies with a Pong echoing the same `sequence` |

### traffic-monitor-app-core (`com.example.monitor`)

All of the monitor's actual Java source and resources (`application.yml`, `static/*`) live here.

| Package | Classes |
|---|---|
| `com.example.monitor` | `TrafficMonitorApplication` — Spring Boot entrypoint; `scanBasePackages` widened to also pick up `com.example.handlerapp` beans |
| `com.example.monitor.config` | `TrafficMonitorProperties` — binds `traffic.*` properties from `application.yml` |
| `com.example.monitor.schema` | `TrafficToolConfig`/`InterfaceConfig`/`MessageConfig` (POJOs for `config/traffic-tool.yml`), `TrafficToolConfigLoader` (SnakeYAML), `MessageSchemaWiringConfig` (`@Bean MessageDefinitionRegistry`, loaded via reflection at startup) |
| `com.example.monitor.model` | `ObservedMessage` — record capturing one decoded/failed inbound packet |
| `com.example.monitor.store` | `RecentMessageStore` — thread-safe bounded `ArrayDeque`, backs `/api/messages/recent` |
| `com.example.monitor.ingestion.udp` | `UdpIngestionRunner` — opens the Fruit and Weather UDP sockets on startup, decodes packets generically via `MessageDefinitionRegistry`, writes to the store, dispatches to `handler-core` |
| `com.example.monitor.publishing` | `MonitorPayloadFactory` (fields map → protocol bytes via `MessageDefinitionRegistry`), `UdpMessagePublisher` (send one datagram), `PeriodicPublisherService` (scheduled repeat send) |
| `com.example.monitor.handler` | `HandlerWiringConfig` — wires the `handler-core` `ReplySender`/`MessageHandlerRegistry`/`MessageArrivedDispatcher` beans on top of `MonitorPayloadFactory`/`UdpMessagePublisher`, resolving reply destinations via `AutoReplySettingsService` |
| `com.example.monitor.autoreply` | `AutoReplySettingsService` — runtime-mutable global + per-interface auto-reply enabled/destination state, seeded from `TrafficToolConfig` |
| `com.example.monitor.api` | `MessageController`, `PublishController`, `PeriodicPublishController`, `AutoReplyController` + their request/response records |

### traffic-monitor-app

No `src/` directory — just a `pom.xml` declaring the `traffic-monitor-app-core` +
`shared-schemas` + `handler-app` dependencies and the `spring-boot-maven-plugin` `repackage`
config (`mainClass=com.example.monitor.TrafficMonitorApplication`, resolvable at runtime
because it's on the classpath via `traffic-monitor-app-core`). `docker-compose.yml`/`Dockerfile`
build and copy this module's jar — everything else is pulled in transitively.

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

Every successfully decoded message is dispatched (asynchronously, off the UDP receive loop) to
a per-message-type `onMessageArrived` hook, so you can react to specific inbound messages —
most commonly by sending a reply. `MessageArrivedHandler<T>` is generic over the concrete
incoming message type, so a handler receives the message **already decoded into its real
type** — no `Map` unpacking, no casting. To add one: implement `MessageArrivedHandler<T>` in
`handler-app` (`@Component`, `interfaceName()`/`messageType()` pick which message it reacts to,
`T` is that message's shared-schemas record) and call `replySender.reply(ProtocolMessage
message, String host, int port)` with a real typed instance (e.g.
`new BananaMessage("yellow", 100.0)`) to send something back. `ReplySender`'s implementation
resolves which `MessageDefinition` to encode with via
`MessageDefinitionRegistry.findByMessageClass(message.getClass())` — no string-based dispatch
on the reply side either.

The third parameter, `DestinationConfig destinationConfig`, is the resolved auto-reply
destination (host/port) for the interface that triggered dispatch — see
[Auto-reply toggle](#auto-reply-toggle) below for where it comes from. It's `null` if that
interface has no destination configured, which handlers should check before replying.

Worked example — `handler-app/.../fruit/OrangeMessageHandler.java` replies with a Banana when
an Orange arrives with `freshness == not_fresh`:

```java
public class OrangeMessageHandler implements MessageArrivedHandler<OrangeMessage> {
    @Override
    public String interfaceName() { return "Fruit Interface"; }

    @Override
    public String messageType() { return "Orange"; }

    @Override
    public void onMessageArrived(OrangeMessage message, ReplySender replySender, DestinationConfig destinationConfig) {
        if (message.freshness() == FruitFreshness.NOT_FRESH && destinationConfig != null) {
            replySender.reply(new BananaMessage("yellow", 100.0), destinationConfig.host(), destinationConfig.port());
        }
    }
}
```

`BananaMessageHandler`, `TemperatureReadingMessageHandler`, and `PingMessageHandler` (always
replies `new PongMessage(message.sequence())`, echoing the same sequence) round out the
registered handlers — `BananaMessageHandler`/`TemperatureReadingMessageHandler` are still empty
stubs with a `// TODO`.

Every `shared-schemas` message record (`OrangeMessage`, `BananaMessage`,
`TemperatureReadingMessage`, `PingMessage`, `PongMessage`) implements `schema-core`'s
`ProtocolMessage` — an empty marker interface that exists purely so `handler-core`'s generic
types can accept "any real protocol message" without depending on `shared-schemas` itself.
`handler-core` depends on `schema-core` for this (its only dependency), which is still fully
Fruit/Weather-agnostic. `MessageDefinition` also grew a typed decode path
(`decodeMessage(ByteBuffer) → ProtocolMessage`, alongside the existing `Map`-returning
`decodeBody` used for the UI) so `UdpIngestionRunner` can hand handlers the real typed object
instead of a generic envelope.

### Auto-reply toggle

Two independent gates control whether `onMessageArrived` actually fires and where the reply
goes, both defaulting from `config/traffic-tool.yml` and both live-editable via the UI/API
without a restart:

- **Global switch** (`AutoReplySettingsService.isGlobalEnabled()`) — a single master on/off for
  the whole mechanism.
- **Per-interface switch + destination** — each interface (Fruit/Weather/Ping) has its own
  `enabled` flag plus a `host`/`port`.

`UdpIngestionRunner.dispatchIfEligible()` checks `autoReplySettingsService.shouldAutoReply
(interfaceName)` (both switches at once) right after the parse-error check — if either is off,
the handler never runs. If it passes, `UdpIngestionRunner` resolves that interface's
`host`/`port` from `AutoReplySettingsService` into a `handler-core` `DestinationConfig` (`null`
if somehow unconfigured) and passes it straight into `onMessageArrived(...)` as the third
argument — the handler explicitly uses `destinationConfig.host()/port()` when it calls
`replySender.reply(...)`. `ReplySender`'s implementation itself does no destination
resolution or overriding — it just sends to whatever host/port it's given, so the whole
destination story lives in one visible place (`UdpIngestionRunner` + the handler), not split
across a silent override inside the reply mechanism.

Defaults come from `config/traffic-tool.yml`:

```yaml
autoReply:
  enabled: false          # global default

interfaces:
  - key: ping
    name: Ping Interface
    messages: [...]
    autoReply:             # per-interface default
      enabled: false
      host: localhost
      port: 7001
```

REST API (`AutoReplyController`):

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET | `/api/autoreply/settings` | — | `{ globalEnabled, interfaces: { <name>: {enabled, host, port} } }` |
| POST | `/api/autoreply/global` | `{ enabled }` | Sets the global switch |
| POST | `/api/autoreply/interface` | `{ interfaceName, enabled, host, port }` | Sets one interface's switch + destination |

Web UI: a new **Auto-Reply** tab — a master toggle switch, plus a collapsible accordion with
one row per interface. The accordion is built entirely from the `GET` response's `interfaces`
map (not hardcoded), so it automatically picks up new interfaces added to
`config/traffic-tool.yml` with no frontend changes.

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
  - mode: FRUIT_ORANGE       # FRUIT_ORANGE | FRUIT_BANANA | WEATHER_TEMPERATURE_READING | PING | TEXT | BASE64 | HEX
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

  builds all seven modules (`schema-core`, `shared-schemas`, `handler-core`, `handler-app`,
  `traffic-monitor-app-core`, `traffic-monitor-app`, `traffic-tester-app`) in dependency order.
- Run the monitor locally without Docker (simplest — from the repo root, after `mvn clean
  package`):

  ```bash
  TRAFFIC_TOOL_CONFIG=config/traffic-tool.yml \
    java -jar traffic-monitor-app/target/traffic-monitor-app-1.0-SNAPSHOT.jar
  ```

  Uses `application.yml` for ports and `config/traffic-tool.yml` for message definitions —
  HTTP on `:8080`, UDP on `:5001`/`:5003`.

  `spring-boot:run` also works, but needs `mvn install` first (not just `package` — direct
  goal invocation, unlike lifecycle phases, doesn't resolve sibling-module dependencies via
  `-am`) and an **absolute** `TRAFFIC_TOOL_CONFIG` path (the plugin's working directory is the
  `traffic-monitor-app/` module folder, not the repo root, so the `config/traffic-tool.yml`
  relative default won't resolve):

  ```bash
  mvn install -DskipTests
  TRAFFIC_TOOL_CONFIG="$(pwd)/config/traffic-tool.yml" mvn -pl traffic-monitor-app spring-boot:run
  ```
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
