# Traffic Interface Tool

Java 21 / Maven multi-module project for simulating and observing custom binary protocol
traffic over UDP and TCP. Two runnable apps: `traffic-monitor-app` (Spring Boot service that
ingests, stores, analyzes, and publishes protocol traffic, with a web UI) and
`traffic-tester-app` (CLI that sends synthetic traffic at the monitor).

**`README.md` is currently stale** (references deleted classes like `FruitProtocolCodec`,
says "seven modules" / "four protocols" — both are now wrong). Don't trust it for module
counts or class names; this file and the code are the source of truth. It should be
regenerated/updated at some point.

## Module graph (8 modules)

```
schema-annotations   @FixedArrayLength, @EnumWireSize. Zero deps.
schema-core          MessageDefinition/Registry, ProtocolMessage marker, ProtocolHeaderCodec
                      (legacy fixed envelope), + the reflective codec engine (see below).
                      Depends on: schema-annotations.
handler-core         MessageArrivedHandler<T>, MessageHandlerRegistry, MessageArrivedDispatcher,
                      ReplySender, DestinationConfig. Depends on: schema-core.
shared-schemas       Concrete message classes (fruit/weather/ping/candy/rada). Depends on:
                      schema-core only.
handler-app          Concrete MessageArrivedHandler implementations, one per message type,
                      including handler-app/rada/RadaTracksExtendedHandler. Depends on:
                      handler-core, shared-schemas.
traffic-monitor-app-core   The generic engine: ingestion, persistence, analytics, auto-reply,
                      publisher, interface runtime control, REST API, UI resources. Depends on
                      schema-core + handler-core ONLY at compile scope — shared-schemas and
                      handler-app are test-scope deps only. This is a hard architectural
                      invariant (see "Core has zero schema dependency" below), not just style.
traffic-monitor-app  The runnable app. Holds TrafficMonitorApplication's main(), depends on
                      traffic-monitor-app-core + shared-schemas + handler-app, holds the
                      spring-boot-maven-plugin config.
traffic-tester-app   Standalone CLI tester, depends on shared-schemas directly (it's a test
                      tool, allowed to know the wire format) + Instancio for random payloads.
```

Build/test a module + its deps: `mvn -pl <module> -am test`. Full repo: `mvn clean verify`
from the root. Integration tests (`*IT.java`, real Spring context + real sockets) run via
`failsafe` during `verify`/`integration-test`, not plain `test`.

## Core architectural invariant: engine has zero schema dependency

`traffic-monitor-app-core` never imports `com.example.schemas.*` or `com.example.handlerapp.*`
in main code — enforced by the pom (those are test-scope deps only). All wiring from generic
engine to concrete protocol classes happens by fully-qualified class name string, read from
YAML config (`config/traffic-tool.yml`) and resolved via `Class.forName` at startup
(`MessageSchemaWiringConfig`). This means new protocols never require touching the engine.

## The reflective codec convention (schema-core)

Messages don't need a hand-written `MessageDefinition` + separate codec class pair anymore.
`ReflectiveStructCodec` reflectively invokes methods/constructors that follow a convention:

- **Decode** (first match wins): `public static T fromByteBuffer(ByteBuffer)` (used by
  records — immutable, can't self-mutate) — else `public T(byte[])` constructor (used by
  mutable classes like the rada messages, which parse themselves in the ctor).
- **Encode** (first match wins): `public byte[] toByteArray()` no-arg, self-sizing (used when
  a field is variable-length, e.g. a `String` — `StructSizeCalculator` can't size those) —
  else `public void toByteArray(ByteBuffer)`, buffer pre-sized via
  `StructSizeCalculator.calculateStructSize(class)` (used for fixed-layout messages; array
  fields need `@FixedArrayLength(n)` from `schema-annotations` for this to work).

`ReflectiveMessageDefinition(interfaceName, messageType, opcode, messageClass)` wraps this
into a `MessageDefinition` — one line of config replaces one hand-written Java class. Config
supports both `definitionClass:` (legacy hand-written) and `messageClass:`+`opcode:`
(reflective) per message entry; all current messages use the reflective style.

`ReflectiveFieldExtractor`/`ReflectiveFieldApplier` convert message objects ↔ generic
`Map<String,Object>` (used for archival/analytics JSON and the generic publisher). Enums with
a `getWireName()` method are represented by that value both ways (case-insensitive on the way
in); everything else falls back to the Java constant name. String field values are coerced to
target numeric/boolean types on the way in — inputs from HTTP/JSON/HTML forms always arrive
as strings, and this bit us once already (see "Gotchas" below).

## Two ingestion paths (dual-path by design, not an accident)

Historically all messages shared two fixed ports (`traffic.udp.fruit-port`/`weather-port`) and
routed by a single global opcode lookup, using a fixed 16-byte envelope
(`ProtocolHeaderCodec`: opcode+timestamp+bodyLength). That path is **unchanged** and still
serves fruit/weather/ping/candy.

Newer interfaces can instead declare a **dedicated port** in `config/traffic-tool.yml`
(`port:`, `protocol:`, `headerType:`, `opcodeFieldName:` on the `InterfaceConfig` entry) — see
the `rada` interface for a real example. These get their own socket
(`UdpIngestionRunner.startInterface`/`stopInterface`), their own header type (parsed via the
same `ReflectiveStructCodec`), and their own scoped `MessageDefinitionRegistry` — a separate
`@Bean Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries` in
`MessageSchemaWiringConfig`, distinct from the legacy global `messageDefinitionRegistry` bean.

**Important semantic difference between the two paths**: for the legacy path, the pipeline
strips the header before calling `MessageDefinition.decodeBody`/`decodeMessage` (body-only
bytes). For the dedicated-port path, the **full payload including header** is passed instead,
because dedicated-port message classes (rada) re-parse their own header as part of their own
decode (e.g. `RadaStatus.fromByteArray` calls `header.fromByteArray(buffer)` first). Don't
"fix" this into stripping the header for both paths — it'll break rada.

Corollary for the publisher (`PublisherService.buildPayload`): legacy interfaces need
`definition.encodeBody(...)` wrapped in `ProtocolHeaderCodec.encodeMessage(opcode, ts, body)`;
dedicated-port interfaces send `definition.encodeBody(...)` as-is (already includes the
header). Branch on `InterfaceConfig.hasDedicatedPort()`.

TCP dedicated-port ingestion is **not implemented** — `TcpIngestionRunner` still only serves
the two legacy ports. Every configured interface today is UDP.

Per-interface runtime start/stop (`/api/interfaces/{key}/start|stop`,
`InterfaceRuntimeRegistry`/`InterfaceControlService`) only applies to dedicated-port
interfaces. Legacy interfaces are all-or-nothing via `traffic.udp.enabled`/`traffic.tcp.enabled`.
`InterfaceControlService.requireUdp()` additionally gates control to protocol `"UDP"`
specifically (not just "has a dedicated port") — currently a no-op distinction since
dedicated-port TCP isn't implemented, but will matter if that gap ever gets filled.

## Config files

- `config/traffic-tool.yml` — the interfaces/messages/auto-reply config, loaded by
  `TrafficToolConfigLoader` (env var `TRAFFIC_TOOL_CONFIG`, default path
  `config/traffic-tool.yml` relative to CWD — run from repo root). This is where
  `messageClass:`/`definitionClass:`, dedicated ports, `headerType:`, broadcast targets, etc.
  live. Test equivalent: `traffic-monitor-app-core/src/test/resources/traffic-tool-test.yml`.
- `traffic-monitor-app-core/src/main/resources/application.yml` — Spring config: server port,
  H2 datasource, `traffic.udp`/`traffic.tcp`/`traffic.store` (legacy fixed-port settings).
- `config/tester-scenario.yml` — traffic-tester-app's scenario definition (what to send, how
  often, to which target).

## Interfaces currently configured

Fruit (Orange, Banana — legacy envelope), Weather (TemperatureReading — legacy envelope), Ping
(Ping, Pong — legacy envelope), Candy (Candy — legacy envelope), Rada (RadaStatus,
RadaExtendedStatus, RadaExtendedStatusMrs, RadaTracksExtended — dedicated port 5050, custom
`RadaHeader`, sample/demo radar-style protocol used to prove out the dedicated-port path).

## Gotchas learned the hard way

- **Spring `Map<String, X>` bean injection**: if you declare `@Bean Map<String, X>` yourself
  AND other beans of type `X` also exist in the context, plain `Map<String, X>`
  constructor-injection silently gets Spring's *implicit* "collect all beans of type X keyed
  by bean name" behavior instead of your explicit bean — your keys get replaced by bean names
  and your entries vanish. Fix: `@Qualifier("yourBeanName")` on the injection point. Bit us in
  `UdpIngestionRunner`/`PublisherMetadataService` with `interfaceMessageDefinitionRegistries`.
- **`@PathVariable`/`@RequestParam` without an explicit name** throws
  `IllegalArgumentException: Name for argument ... not specified` at request time (not compile
  time) because this project doesn't compile with `-parameters`. Always write
  `@PathVariable("key") String key`, not bare `@PathVariable String key`.
- **String→numeric coercion in `ReflectiveFieldApplier`**: HTML form inputs and generic JSON
  clients send every field value as a string. `coerce()` must handle `String` → primitive
  numeric/boolean targets, not just `Number` → primitive. Found via an actual browser
  Playwright test of the generic publisher UI, not by unit tests (they'd only ever passed
  properly-typed values like `Map.of("calories", 80.0)`).
- **`StructSizeCalculator` can't size `String` fields** — messages with a variable-length
  string (Orange/Banana/Candy/TemperatureReading) must use the no-arg self-sizing
  `toByteArray()` encode path, not the `StructSizeCalculator`-sized `toByteArray(ByteBuffer)`
  path.
- **Instancio + `@FixedArrayLength`**: Instancio doesn't know about this project's custom
  annotation and will generate arrays of its own default length, which then mismatches what
  `StructSizeCalculator` allocates. `RadaTracksExtended` (array-heavy) is deliberately *not*
  wired into the tester app's Instancio generation for this reason — only `RadaStatus`
  (scalar-only) is. Fixing this needs explicit `Instancio.of(...).generate(field(...), gen ->
  gen.array().length(n))` per annotated array field.

## Known gaps / natural follow-ups

- TCP dedicated-port ingestion (see above).
- `RadaTracksExtended` Instancio generation (see above).
- Multi-select interface filtering in the Live/History UI tabs — dropdowns are dynamic now
  (all 5 interfaces show up) but still single-select; true multi-select needs
  `HistoryController`/`AnalyticsController` to accept a repeatable `interfaceName` param.
- No standalone Spring-free ingestion library module or a second thin deployable app module
  for customer-specific handler bundles — that capability exists conceptually (extend
  `handler-app`-shaped modules) but isn't split into separate reusable artifacts.
