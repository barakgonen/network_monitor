# Traffic Interface Tool

A small Java 21 / Maven multi-module toolkit for simulating and observing custom binary
protocol traffic over **UDP and TCP**. It has two runnable apps that talk to each other:

- **traffic-monitor-app** — a Spring Boot service that listens for UDP and TCP traffic, decodes
  it, stores recent messages in memory (plus a durable H2-backed history with search/analytics
  endpoints), exposes a REST API, and serves a dark-themed live-monitoring web UI. It can also
  *publish* messages over UDP or TCP (once or on a repeating schedule), can auto-reply to
  specific inbound message types via pluggable handlers (over UDP or TCP, independent of the
  inbound transport), and exposes Micrometer/Actuator metrics (including a Prometheus scrape
  endpoint) for both transports.
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
- **traffic-monitor-app-core** — the generic engine itself, as a plain library: ingestion,
  store, REST API, UI resources, publishing, reflection/scan-based wiring. Depends on
  `schema-core` + `handler-core` only — **not** `shared-schemas`, **not** `handler-app` — and
  has no bootable `main()`/`spring-boot-maven-plugin` of its own. This is the module a client
  would take a dependency on to build their *own* monitor with their *own* protocol and
  handlers.
- **traffic-monitor-app** — the runnable application. Holds the `TrafficMonitorApplication`
  Spring Boot entrypoint (`main()`), depends on `traffic-monitor-app-core` + `shared-schemas` +
  `handler-app`, and holds the `spring-boot-maven-plugin` config that produces the final
  runnable/deployable jar. A client building their own monitor would write their own thin `-app`
  module shaped exactly like this one, swapping in their own schema/handler modules (or reusing
  ours).

Four demo protocols currently exist: **Fruit Interface** (Orange, Banana messages),
**Weather Interface** (TemperatureReading messages), **Ping Interface** (Ping, Pong messages),
and **Candy Interface** (Candy messages).

## Architecture

```
              UDP/TCP :5001 (Fruit)
  traffic-tester ───────────────────► traffic-monitor
       app        UDP/TCP :5003 (Weather)   app
                  ───────────────────►   (Spring Boot,
                                          port :8080 HTTP+UI+REST,
                                          port :8080/actuator/* metrics)
                  UDP :7001 (listener)
  traffic-tester ◄───────────────────  traffic-monitor
       app       single/periodic publish,      app
                  or auto-reply (UDP or TCP,
                  independent of inbound transport)
```

The monitor persists every observed message (UDP or TCP) to an embedded H2 database in addition
to the in-memory recent-message ring buffer, and exposes `/api/messages/history` +
`/api/analytics/*` for querying that durable history. See
[Persistence and history](#persistence-and-history) and
[Metrics and observability](#metrics-and-observability) below.

```
   schema-core          handler-core          <-- the generic engine's contracts (SDK)
   (generic)             (generic)
        ▲                     ▲
        └──────────┬──────────┘
                    │  (compile dependency; zero schema/handler imports)
         traffic-monitor-app-core             <-- the generic engine itself, a plain library
                    ▲                             (no main(), no spring-boot-maven-plugin)
                    │
   shared-schemas ──┼── handler-app           <-- our concrete plugin implementations
        ▲           │        ▲                    (a client could supply their own instead)
        └───────────┼────────┘
                    traffic-monitor-app        <-- the runnable app (client pattern);
                    │                              holds TrafficMonitorApplication's main()
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
  a source-code convention. It has no bootable `main()` of its own — it's a library. A client
  can depend on this module's published jar directly. (Its own integration tests boot a
  test-scoped `TrafficMonitorTestApplication` in `src/test/java`, since the module has nothing
  bootable in `src/main`.)
- `traffic-monitor-app` is the only module that depends on `traffic-monitor-app-core` +
  `shared-schemas` + `handler-app` together, and the only one that runs
  `spring-boot-maven-plugin`'s `repackage` goal — it owns the `TrafficMonitorApplication`
  entrypoint (`main()`), and its final fat jar is what the Dockerfile copies. This module is the
  *pattern* a client replicates: their own thin `-app` module depending on
  `traffic-monitor-app-core` + their own schema/handler modules (or ours), with their own
  entrypoint class.
- `traffic-tester-app` is a plain Java app (no Spring) with its own `main()`.

## Wire protocols

All multi-byte numeric fields are **big-endian**. All strings are **UTF-8, length-prefixed**
with a 4-byte `int32` length. Every message starts with the same 16-byte header. Framing is
**transport-agnostic**: the same header+body layout is used whether a message arrives as one UDP
datagram or over a TCP stream — `ProtocolHeaderCodec` (in `schema-core`) is the single
implementation shared by both `UdpIngestionRunner` and `TcpIngestionRunner`. Over TCP, since the
stream has no inherent message boundaries, the receiver reads the fixed 16-byte header first to
learn `bodyLength`, then reads exactly that many more bytes to reassemble one logical message
before decoding — this lets a single persistent TCP connection carry multiple messages
back-to-back.

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

### Candy Interface

A second extensibility example, this time with a string+double body (rather than Ping's bare
int32): send a Candy message, the monitor decodes and archives it. No auto-reply — the handler is
a no-op stub, same as `BananaMessageHandler`. Also demonstrates the tester sending over **TCP**
(`target.transport: TCP` in `config/tester-scenario.yml`), reusing the monitor's Fruit TCP port
(`:5001`) since TCP ingestion decodes generically by opcode, independent of interface.

| Message | Opcode |
|---|---|
| Candy | 4001 |

**Candy body:**

| Field | Type |
|---|---|
| `name` | UTF-8 string |
| `calories` | float64 |

`handler-app`'s `PingMessageHandler` replies to every Ping with a Pong echoing the same
`sequence`, sent back to the sender's address:port — see
[Auto-reply message handlers](#auto-reply-message-handlers).

Codecs validate header size (≥12 bytes) and that `bodyLength` matches the remaining buffer.

## Module reference

### schema-core (`com.example.schemacore`)

| Class | Purpose |
|---|---|
| `ProtocolHeader` (record) | Generic header: `opcode`, `sendTimeEpochMillis`, `bodyLength` |
| `ProtocolHeaderCodec` | `decodeHeader(ByteBuffer)` / `encodeMessage(opcode, sendTime, body)` — the one place the 16-byte framing is implemented |
| `ProtocolMessage` (interface) | Empty marker interface — every concrete message record (`OrangeMessage`, `BananaMessage`, ...) implements it, purely so generic code (`ReplySender`) can accept "a real message" without depending on `shared-schemas` |
| `MessageDefinition` (interface) | `interfaceName()`, `messageType()`, `opcode()`, `messageClass()`, `decodeBody(ByteBuffer)` → `Map` (for `ObservedMessage`/UI display), `decodeMessage(ByteBuffer)` → `ProtocolMessage` (for typed handler dispatch), `encodeBody(Map)` (for the REST publish API), `encodeBody(ProtocolMessage)` (for `ReplySender`) — one implementation per real message type |
| `MessageDefinitionRegistry` | Built from a `List<MessageDefinition>`; keyed by opcode, by interfaceName/messageType, **and** by `messageClass()` (so a typed instance can resolve its own definition); `loadFromClassNames(List<String>)` does the reflective `Class.forName` + `newInstance()` loading; fail-fast on duplicate keys in any index |
| `MessageFields` | Static `requireString`/`requireDouble`/`requireInt` field-validation helpers shared by the `Map`-based `encodeBody` implementations |

### shared-schemas (`com.example.schemas`)

| Package | Classes |
|---|---|
| `com.example.schemas` | `ProtocolType` (TCP/UDP enum) |
| `com.example.schemas.fruit` | `FruitOpcodes`, `FruitProtocolHeader`, `FruitProtocolCodec` (encode/decode, used directly by `traffic-tester-app`), `FruitFreshness` (enum), `OrangeMessage`, `BananaMessage`, `OrangeMessageDefinition`, `BananaMessageDefinition` (the `schema-core` implementations, loaded reflectively by `traffic-monitor-app`) |
| `com.example.schemas.weather` | `WeatherOpcodes`, `WeatherProtocolHeader`, `WeatherProtocolCodec`, `WeatherCondition` (enum), `TemperatureReadingMessage`, `TemperatureReadingMessageDefinition` |
| `com.example.schemas.ping` | `PingOpcodes`, `PingMessage`, `PongMessage`, `PingProtocolCodec` (encode/decode, used directly by `traffic-tester-app`), `PingMessageDefinition`, `PongMessageDefinition` — the extensibility proof interface |
| `com.example.schemas.candy` | `CandyOpcodes`, `CandyMessage`, `CandyProtocolCodec` (encode/decode, used directly by `traffic-tester-app`), `CandyMessageDefinition` — the string+double, TCP-send extensibility example |

### handler-core (`com.example.handlercore`)

Depends on `schema-core` (its only dependency) purely for the `ProtocolMessage` type — still
zero Fruit/Weather knowledge.

| Class | Purpose |
|---|---|
| `DestinationConfig` (record) | `(String host, int port, String transport)` — the resolved auto-reply destination (and reply transport, `"UDP"`/`"TCP"`) for the interface that just fired, handed to the handler so it doesn't have to guess |
| `ReplySender` (interface) | `reply(ProtocolMessage message, String host, int port, String transport)` — construct a real typed message instance and send it to an explicit host/port over the given transport |
| `MessageArrivedHandler<T>` (interface) | Generic over the concrete incoming message type. `interfaceName()`, `messageType()`, `onMessageArrived(T message, ReplySender replySender, DestinationConfig destinationConfig)` — `message` arrives already decoded into its real type (e.g. `OrangeMessage`), no `Map` unpacking needed; `destinationConfig` is `null` if no destination is configured for that interface |
| `MessageHandlerRegistry` | Keyed by interfaceName/messageType; fail-fast on duplicates |
| `MessageArrivedDispatcher` | Looks up the registry and invokes the matching handler, if any |

### handler-app (`com.example.handlerapp`)

| Package | Classes |
|---|---|
| `com.example.handlerapp.fruit` | `OrangeMessageHandler` (worked example — auto-replies with a Banana when `freshness == not_fresh`), `BananaMessageHandler` (stub) |
| `com.example.handlerapp.weather` | `TemperatureReadingMessageHandler` (stub) |
| `com.example.handlerapp.ping` | `PingMessageHandler` — always auto-replies with a Pong echoing the same `sequence` |
| `com.example.handlerapp.candy` | `CandyMessageHandler` (stub) |

### traffic-monitor-app-core (`com.example.monitor`)

Almost all of the monitor's Java source and resources (`application.yml`, `static/*`) live here,
as a plain library — no bootable `main()`.

| Package | Classes |
|---|---|
| `com.example.monitor.config` | `TrafficMonitorProperties` — binds `traffic.*` properties from `application.yml` (`udp.*`, `tcp.*`, `store.*`) |
| `com.example.monitor.schema` | `TrafficToolConfig`/`InterfaceConfig`/`MessageConfig`/`AutoReplyConfig`/`AutoReplyDestinationConfig` (POJOs for `config/traffic-tool.yml`, the latter carrying the optional per-interface `transport`), `TrafficToolConfigLoader` (SnakeYAML), `MessageSchemaWiringConfig` (`@Bean MessageDefinitionRegistry`, loaded via reflection at startup) |
| `com.example.monitor.model` | `ObservedMessage` — record capturing one decoded/failed inbound packet, transport-tagged (`transportProtocol` is `"UDP"` or `"TCP"`) |
| `com.example.monitor.store` | `RecentMessageStore` — thread-safe bounded `ArrayDeque`, backs `/api/messages/recent` (in-memory only; see `persistence` for the durable equivalent) |
| `com.example.monitor.ingestion` | `MessageIngestionPipeline` — the shared decode/store/archive/dispatch/metrics pipeline used by **both** `UdpIngestionRunner` and `TcpIngestionRunner`, guaranteeing identical behavior and metrics regardless of transport |
| `com.example.monitor.ingestion.udp` | `UdpIngestionRunner` — opens the Fruit and Weather UDP sockets on startup, decodes packets generically via `MessageDefinitionRegistry`, delegates each datagram to `MessageIngestionPipeline` |
| `com.example.monitor.ingestion.tcp` | `TcpIngestionRunner` — opens Fruit and Weather TCP `ServerSocket`s, accepts persistent connections (one thread per connection via a cached thread pool), reads the 16-byte header + declared `bodyLength` off the stream to reassemble each logical message, delegates to `MessageIngestionPipeline`; tracks active connections for the `network_monitor.tcp.connections.active` gauge |
| `com.example.monitor.persistence` | `MessageArchiveRepository` (JDBC/H2-backed durable store — every ingested message, UDP or TCP, is archived here asynchronously), `HistoryQuery`/`HistoryPage` (paged filtered history lookups), `GroupByField`/`BreakdownCount`/`TimeBucket`/`TimeBucketCount` (analytics aggregation types) |
| `com.example.monitor.publishing` | `MonitorPayloadFactory` (fields map → protocol bytes via `MessageDefinitionRegistry`), `UdpMessagePublisher` (send one datagram), `TcpMessagePublisher` (open a short-lived TCP connection, write one message, close), `TransportSelector` (single source of truth: `normalize(String)` — null/blank → `"UDP"`, else validates `UDP`/`TCP` or throws), `PeriodicPublisherService` (scheduled repeat send, UDP or TCP) |
| `com.example.monitor.handler` | `HandlerWiringConfig` — wires the `handler-core` `ReplySender`/`MessageHandlerRegistry`/`MessageArrivedDispatcher` beans on top of `MonitorPayloadFactory`/`UdpMessagePublisher`/`TcpMessagePublisher`, branching on `TransportSelector.normalize(transport)` to pick the outbound publisher, resolving reply destinations via `AutoReplySettingsService` |
| `com.example.monitor.autoreply` | `AutoReplySettingsService` — runtime-mutable global + per-interface auto-reply enabled/destination/**transport** state, seeded from `TrafficToolConfig` (transport always normalized to `UDP`/`TCP`, never left null) |
| `com.example.monitor.api` | `MessageController` (`/api/messages/recent`), `PublishController` (`/api/publish/udp`, UDP or TCP per request), `PeriodicPublishController`, `AutoReplyController` (settings + global/interface toggles, transport-aware), `HistoryController` (`/api/messages/history`), `AnalyticsController` (`/api/analytics/timeseries`, `/api/analytics/breakdown`) + their request/response records |

### traffic-monitor-app (`com.example.monitor`)

The runnable application. `pom.xml` declares the `traffic-monitor-app-core` + `shared-schemas` +
`handler-app` dependencies and the `spring-boot-maven-plugin` `repackage` config
(`mainClass=com.example.monitor.TrafficMonitorApplication`). `docker-compose.yml`/`Dockerfile`
build and copy this module's jar — everything else is pulled in transitively.

| Package | Classes |
|---|---|
| `com.example.monitor` | `TrafficMonitorApplication` — Spring Boot entrypoint (`main()`); `scanBasePackages` widened to also pick up `com.example.handlerapp` beans |

### traffic-tester-app (`com.example.tester`)

| Package | Classes |
|---|---|
| `com.example.tester` | `TesterMain` — entrypoint; loads scenario, runs send loop, starts listener |
| `com.example.tester.config` | `ScenarioLoader` (SnakeYAML), `TesterScenario`, `PayloadConfig`, `PayloadMode` (enum), `FruitPayloadConfig`, `WeatherPayloadConfig`, `PingPayloadConfig`, `CandyPayloadConfig`, `UdpConfig`, `UdpListenerConfig`, `PayloadTargetConfig` (`host`/`port`/`transport` override) |
| `com.example.tester.payload` | `PayloadFactory` — dispatches on `PayloadMode` to the shared-schemas codecs (or raw text/base64/hex) |
| `com.example.tester.udp` | `UdpPublisher` (send), `UdpListener` (background receive + best-effort Fruit/Weather decode + log) — UDP only, no TCP listener |
| `com.example.tester.tcp` | `TcpPublisher` — opens a short-lived TCP connection, writes one message, closes; used when `target.transport: TCP` |

## traffic-monitor-app details

### Configuration (real)

Two separate config sources, both live:

**`application.yml`** (Spring Boot) — HTTP port, which UDP/TCP ports to listen on, the
recent-message store size, the H2 datasource used for durable history, and Actuator endpoint
exposure:

```yaml
server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

spring:
  datasource:
    url: jdbc:h2:file:${TRAFFIC_MONITOR_DB_PATH:./data/traffic-monitor};AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  sql:
    init:
      mode: always

traffic:
  udp:
    enabled: true
    fruit-port: 5001
    weather-port: 5003
    buffer-size-bytes: 65507
  tcp:
    enabled: true
    fruit-port: 5001
    weather-port: 5003
    max-body-length-bytes: 65507
  store:
    max-size: 500
```

`traffic.udp.*`/`traffic.tcp.*` are bound via `@ConfigurationProperties(prefix = "traffic")`
(`TrafficMonitorProperties`). Fruit and Weather intentionally share the same port numbers across
UDP and TCP (`5001`/`5003`) — they're independent OS-level namespaces, so there's no conflict;
Docker Compose maps each one twice (`5001:5001/udp` and `5001:5001/tcp`). Either transport can be
disabled independently via `traffic.udp.enabled`/`traffic.tcp.enabled`.

The H2 database file (`TRAFFIC_MONITOR_DB_PATH`, default `./data/traffic-monitor`) is where
every observed message is durably archived — see
[Persistence and history](#persistence-and-history). `spring.sql.init.mode: always` runs the
schema migration in `src/main/resources/schema.sql` on every startup (idempotent
`CREATE TABLE IF NOT EXISTS`).

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
`MessageIngestionPipeline.ingest(...)` then decodes any inbound payload generically — whether it
arrived via `UdpIngestionRunner` (one datagram) or `TcpIngestionRunner` (one reassembled
message off a persistent connection): read the 16-byte header via `ProtocolHeaderCodec`, look up
the opcode in the registry, call `decodeBody(...)` on whatever `MessageDefinition` matched — no
`"Fruit"`/`"Weather"` or `"UDP"`/`"TCP"` branching in the decode logic at all. The same pipeline
call also records metrics, writes to the in-memory store, archives to H2, and dispatches to
`handler-core` for auto-reply — see [Metrics and observability](#metrics-and-observability).
`MonitorPayloadFactory` does the mirror image for outgoing messages (REST publish API and the
auto-reply `ReplySender` below), looking up by `interfaceName`/`messageType` instead of opcode.

### REST API

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET | `/api/messages/recent` | — | Returns the current in-memory `ObservedMessage` list (newest first) |
| GET | `/api/messages/history` | — (query params) | Paged, filtered search over the durable H2 history — see [Persistence and history](#persistence-and-history) |
| GET | `/api/analytics/timeseries` | — (query params) | Message counts bucketed by time — see [Persistence and history](#persistence-and-history) |
| GET | `/api/analytics/breakdown` | — (query params) | Message counts grouped by `interfaceName` or `messageType` — see [Persistence and history](#persistence-and-history) |
| POST | `/api/publish/udp` | `PublishRequest` | Sends one message over UDP or TCP (despite the path name — `transport` in the body selects the transport, default UDP) |
| POST | `/api/publish/udp/periodic/start` | `PeriodicPublishRequest` | Starts repeating publish (UDP or TCP, per the nested `PublishRequest.transport`) |
| POST | `/api/publish/udp/periodic/stop` | — | Stops the periodic publisher |
| GET | `/api/publish/udp/periodic/status` | — | Returns current `PeriodicPublishStatus` |
| GET | `/api/autoreply/settings` | — | Returns global + per-interface auto-reply settings (including `transport`) |
| POST | `/api/autoreply/global` | `{ enabled }` | Sets the global auto-reply switch |
| POST | `/api/autoreply/interface` | `{ interfaceName, enabled, host, port, transport }` | Sets one interface's switch + destination + reply transport |

`PublishRequest`: `interfaceName`, `messageType`, `host`, `port`, `transport` (`"UDP"` \| `"TCP"`,
optional — blank/omitted defaults to `"UDP"`), `fields` (`Map<String,Object>`).

`PeriodicPublishRequest`: `publishRequest` (a `PublishRequest`), `eventsPerTimeUnit` (int),
`timeUnit` (`SECOND` | `MINUTE` | `HOUR`).

`PeriodicPublishStatus`: `running`, `interfaceName`, `messageType`, `host`, `port`,
`eventsPerTimeUnit`, `timeUnit`, `intervalMillis`, `sentCount`, `lastError`.

Example single-publish request (add `"transport": "TCP"` to send over TCP instead):

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

Served at `http://localhost:8080`, dark "System Flow Investigator" theme, four tabs:

- **Live Messages** — table of `Observed At | Protocol | Port | Interface | Message Name |
  Message Body | Parse Error`, polling `/api/messages/recent` every ~2s, with a text filter and
  a click-to-inspect JSON detail panel. Raw Base64 is not shown in the table. `Protocol` reflects
  the actual inbound transport (`UDP` or `TCP`) for that message.
- **Sample Publisher** — form to pick interface/message, target host:port, a UDP/TCP transport
  selector, and per-field inputs (enum fields render as dropdowns), with "Send Once" plus
  periodic controls (events/time-unit, Start/Stop/Refresh Status).
- **Auto-Reply** — see [Auto-reply toggle](#auto-reply-toggle) below.
- **History** — search/filter the durable H2-backed history (interface, message type, parse-error
  only, time range, paging) and view analytics (time-series counts, breakdown by interface or
  message type) — see [Persistence and history](#persistence-and-history).

### Auto-reply message handlers

Every successfully decoded message is dispatched (asynchronously, off the UDP/TCP receive path)
to a per-message-type `onMessageArrived` hook, so you can react to specific inbound messages —
most commonly by sending a reply. `MessageArrivedHandler<T>` is generic over the concrete
incoming message type, so a handler receives the message **already decoded into its real
type** — no `Map` unpacking, no casting. To add one: implement `MessageArrivedHandler<T>` in
`handler-app` (`@Component`, `interfaceName()`/`messageType()` pick which message it reacts to,
`T` is that message's shared-schemas record) and call `replySender.reply(ProtocolMessage
message, String host, int port, String transport)` with a real typed instance (e.g.
`new BananaMessage("yellow", 100.0)`) to send something back. The reply transport is independent
of the transport the triggering message arrived on — e.g. a message received over UDP can still
trigger a reply sent over TCP, per the interface's configured auto-reply `transport`.
`ReplySender`'s implementation resolves which `MessageDefinition` to encode with via
`MessageDefinitionRegistry.findByMessageClass(message.getClass())` — no string-based dispatch
on the reply side either.

The third parameter, `DestinationConfig destinationConfig`, is the resolved auto-reply
destination (host/port/transport) for the interface that triggered dispatch — see
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
            replySender.reply(new BananaMessage("yellow", 100.0), destinationConfig.host(), destinationConfig.port(), destinationConfig.transport());
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
`decodeBody` used for the UI) so `MessageIngestionPipeline` can hand handlers the real typed
object instead of a generic envelope, regardless of which transport it arrived on.

### Auto-reply toggle

Two independent gates control whether `onMessageArrived` actually fires and where the reply
goes, both defaulting from `config/traffic-tool.yml` and both live-editable via the UI/API
without a restart:

- **Global switch** (`AutoReplySettingsService.isGlobalEnabled()`) — a single master on/off for
  the whole mechanism.
- **Per-interface switch + destination** — each interface (Fruit/Weather/Ping) has its own
  `enabled` flag plus a `host`/`port`/**`transport`**.

`MessageIngestionPipeline.dispatchIfEligible()` checks `autoReplySettingsService.shouldAutoReply
(interfaceName)` (both switches at once) right after the parse-error check — if either is off,
the handler never runs. If it passes, the pipeline resolves that interface's
`host`/`port`/`transport` from `AutoReplySettingsService` into a `handler-core`
`DestinationConfig` (`null` if somehow unconfigured) and passes it straight into
`onMessageArrived(...)` as the third argument — the handler explicitly uses
`destinationConfig.host()/port()/transport()` when it calls `replySender.reply(...)`.
`ReplySender`'s implementation itself does no destination resolution or overriding — it just
sends to whatever host/port/transport it's given (branching to `UdpMessagePublisher` or
`TcpMessagePublisher` via `TransportSelector.normalize(...)`), so the whole destination story
lives in one visible place (`MessageIngestionPipeline` + the handler), not split across a silent
override inside the reply mechanism. `transport` is optional in config/API — unset or blank
always normalizes to `"UDP"` via `TransportSelector`, so existing UDP-only configs keep working
unchanged.

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
      transport: UDP        # optional — "UDP" or "TCP", defaults to UDP if omitted
```

See [REST API](#rest-api) above for the `AutoReplyController` endpoints
(`/api/autoreply/settings`, `/api/autoreply/global`, `/api/autoreply/interface`).

Web UI: an **Auto-Reply** tab — a master toggle switch, plus a collapsible accordion with one row
per interface, each with a host/port input and a UDP/TCP transport dropdown. The accordion is
built entirely from the `GET` response's `interfaces` map (not hardcoded), so it automatically
picks up new interfaces added to `config/traffic-tool.yml` with no frontend changes.

### Persistence and history

Every message `MessageIngestionPipeline.ingest(...)` observes (successfully decoded or not, UDP
or TCP) is written to two places: `RecentMessageStore` (in-memory, bounded ring buffer, backs
`/api/messages/recent`, lost on restart) and `MessageArchiveRepository` (durable, backs
`/api/messages/history` and `/api/analytics/*`, survives restarts). Archiving happens
asynchronously on a background thread pool so a slow/failed write never blocks ingestion — a
failure is logged and increments the `network_monitor.archive.failures` metric rather than
propagating.

Storage is an embedded H2 database, file-backed by default
(`jdbc:h2:file:${TRAFFIC_MONITOR_DB_PATH:./data/traffic-monitor};AUTO_SERVER=TRUE` — see
[Configuration (real)](#configuration-real)), so history survives container/process restarts as
long as the `./data` volume is preserved. The schema is one `messages` table
(`src/main/resources/schema.sql`, created idempotently on every startup via
`spring.sql.init.mode: always`) mirroring `ObservedMessage`'s fields, with indexes on
`observed_at`, `interface_name`, and `message_type`.

`GET /api/messages/history` (`HistoryController`) — paged, filtered search:

| Query param | Type | Notes |
|---|---|---|
| `interfaceName` | string, optional | exact match |
| `messageType` | string, optional | exact match |
| `parseErrorOnly` | boolean, default `false` | only rows with a non-null `parseError` |
| `from` / `to` | ISO-8601 instant, optional | `observed_at` range |
| `limit` | int, default `50`, max `500` | |
| `offset` | int, default `0` | |

Returns `HistoryResponse`: `items` (`List<ObservedMessage>`), `totalCount`, `limit`, `offset`.

`GET /api/analytics/timeseries` (`AnalyticsController`) — message counts bucketed by time:

| Query param | Type | Notes |
|---|---|---|
| `from` / `to` | ISO-8601 instant, optional | defaults to the last 24 hours |
| `bucket` | string, default `hour` | one of `minute`, `hour`, `day` |

Returns `TimeSeriesResponse`: `bucket`, `points` (`List<{bucketStart, count}>`).

`GET /api/analytics/breakdown` (`AnalyticsController`) — message counts grouped by field:

| Query param | Type | Notes |
|---|---|---|
| `groupBy` | string, required | `interfaceName` or `messageType` |
| `from` / `to` | ISO-8601 instant, optional | defaults to all-time |

Returns `BreakdownResponse`: `groupBy`, `entries` (`List<{key, count}>`).

Both controllers respond `400 Bad Request` with a plain-text message for invalid params (bad
instant format, unknown `bucket`/`groupBy` value).

Web UI: the **History** tab combines a filterable/paged search table (backed by
`/api/messages/history`) with time-series and breakdown charts (backed by `/api/analytics/*`).

### Metrics and observability

`spring-boot-starter-actuator` + `micrometer-registry-prometheus` are on the classpath, exposing
`health`, `info`, `metrics`, and `prometheus` under `/actuator/*` (see
[Configuration (real)](#configuration-real) for the `management.endpoints.web.exposure.include`
setting). `/actuator/health` includes the H2 datasource health indicator automatically, with no
extra code.

Application-specific metrics, all prefixed `network_monitor.*` and tagged with `transport`
(`UDP`/`TCP`) where relevant, so Prometheus queries can slice by transport:

| Metric | Type | Tags | Where it's recorded |
|---|---|---|---|
| `network_monitor.messages.received` | Counter | `transport`, `interfaceName`, `parseError` | `MessageIngestionPipeline.ingest(...)` — once per inbound message, UDP or TCP |
| `network_monitor.messages.payload_size_bytes` | DistributionSummary | `transport` | `MessageIngestionPipeline.ingest(...)` |
| `network_monitor.archive.failures` | Counter | `transport` | `MessageIngestionPipeline` — H2 archive write failed |
| `network_monitor.dispatch.failures` | Counter | `interfaceName` | `MessageIngestionPipeline` — an `onMessageArrived` handler threw |
| `network_monitor.tcp.connections.accepted` | Counter | `port` | `TcpIngestionRunner.acceptLoop` — once per accepted TCP connection |
| `network_monitor.tcp.connections.active` | Gauge | — | `TcpIngestionRunner` — current open TCP connection count (Fruit + Weather combined) |
| `network_monitor.tcp.connections.errors` | Counter | `port` | `TcpIngestionRunner.handleConnection` — genuine connection-handling errors (not expected EOF-on-close) |
| `network_monitor.udp.listener.errors` | Counter | `port` | `UdpIngestionRunner` — genuine socket errors while listening |
| `network_monitor.messages.sent` | Counter | `transport` | `UdpMessagePublisher`/`TcpMessagePublisher` — successful outbound send (REST publish, periodic publish, or auto-reply) |
| `network_monitor.messages.send_errors` | Counter | `transport` | `UdpMessagePublisher`/`TcpMessagePublisher` — outbound send failed |

Deliberately **not** tagged with `messageType` — with two transports × several interfaces ×
several message types, adding a third high-cardinality dimension risked an unbounded label
cardinality blowup in Prometheus; `interfaceName` is enough to slice by without that risk.

Example: scrape counts for Fruit-interface traffic split by transport:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep 'network_monitor_messages_received_total{.*Fruit Interface'
```

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
      transport: UDP          # optional — "UDP" (default) or "TCP", selects the send transport for this message
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
  `udp.*`) over the resolved transport (`target.transport`, default `UDP`; `TCP` opens a
  short-lived connection per message via `TcpPublisher`), sleeping `intervalMillis` between
  iterations.
- **Listener**: if `listener.enabled`, a background thread binds `listener.port` **over UDP
  only** (there is no TCP listener in `traffic-tester-app`), logs each received packet's source,
  hex, and UTF-8 text, and attempts to decode it as a Fruit or Weather message, logging the
  parsed fields on success. Runs for `listener.durationSeconds`. To receive a TCP auto-reply from
  the monitor, point the monitor's auto-reply destination at something else that actually listens
  on TCP (e.g. `nc -l`) — the tester's own listener won't see it.

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

Exposed ports: `8080/tcp` (monitor HTTP+UI+REST+`/actuator/*`), `5001/udp` + `5001/tcp` (Fruit),
`5003/udp` + `5003/tcp` (Weather), `7001/udp` (tester listener). Fruit/Weather are mapped twice
in `docker-compose.yml` (once per transport) since UDP and TCP are independent OS-level port
namespaces.

The `./data` host directory is bind-mounted into the container (`docker-compose.yml`) and holds
the H2 database file backing message history — it survives `docker compose down`/`up` since it's
a host bind mount, not a named volume. Delete `./data` manually to start with an empty history.

Check the monitor is healthy:

```bash
curl http://localhost:8080/actuator/health
```

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
  HTTP+`/actuator/*` on `:8080`, UDP and TCP both on `:5001`/`:5003`. History persists to the H2
  file at `./data/traffic-monitor` (relative to the working directory) unless
  `TRAFFIC_MONITOR_DB_PATH` is set.

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

- **`traffic-tester-app`'s listener is UDP-only.** It can *send* over TCP
  (`target.transport: TCP` in a scenario message — see [Scenario configuration](#scenario-configuration)),
  but `UdpListener` only binds a UDP socket, so it can't receive a TCP auto-reply from the
  monitor. There is no `TcpListener` counterpart yet.
- **`BananaMessageHandler`/`TemperatureReadingMessageHandler`/`CandyMessageHandler` are still
  empty stubs** (`// TODO`) — see [Auto-reply message handlers](#auto-reply-message-handlers).
  Only `OrangeMessageHandler` and `PingMessageHandler` do anything on arrival.
- **`network_monitor.tcp.connections.active` is a single combined gauge**, not split per port
  (Fruit vs Weather) — a deliberate simplicity choice, see
  [Metrics and observability](#metrics-and-observability).
