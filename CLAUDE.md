# Traffic Interface Tool

Java 21 / Maven multi-module project for simulating and observing custom binary protocol
traffic over UDP and TCP. Two runnable apps: `traffic-monitor-app` (Spring Boot service that
ingests, stores, analyzes, and publishes protocol traffic, with a web UI) and
`traffic-tester-app` (CLI that sends synthetic traffic at the monitor).

**`README.md` is currently stale** (references deleted classes like `FruitProtocolCodec`,
says "seven modules" / "four protocols" — both are now wrong). Don't trust it for module
counts or class names; this file and the code are the source of truth. It should be
regenerated/updated at some point.

## Module graph (3 modules)

```
traffic-monitor-app-core   The generic engine, plus what used to be two separate modules
                      (schema-core, handler-core) folded directly into it — merged because
                      handler-core and shared-schemas both compile-depended on schema-core, and
                      this module already compile-depended on handler-core, so schema-core
                      couldn't move here alone without a cycle. Package layout:
                        - `com.example.schemacore` (+ `.annotation`/`.envelope`/`.reflect`
                          sub-packages) — MessageDefinition/Registry, ProtocolMessage marker,
                          the legacy fixed envelope codec, the reflective codec engine.
                        - `com.example.handlercore` — MessageArrivedHandler<T>,
                          MessageHandlerRegistry, MessageArrivedDispatcher, ReplySender,
                          DestinationConfig.
                        - `com.example.monitor` — the engine itself: ingestion, persistence,
                          analytics, auto-reply, publisher, interface runtime control, REST API,
                          UI resources.
                      Has zero compile dependency on shared-schemas/handler-app (see invariant
                      below) — its own test tree only holds pure unit/slice tests; the real
                      end-to-end integration-test suite lives in traffic-monitor-app instead
                      (see "IT suite lives in traffic-monitor-app" below).
traffic-monitor-app  The runnable app, and also what used to be two more separate modules
                      (shared-schemas, handler-app) folded directly into it — merged the same
                      way, since neither has any other consumer besides this module and
                      traffic-tester-app (which now depends on this module instead; see the
                      exec-jar note below). Package layout adds two more top-level packages
                      alongside `com.example.monitor` (the app's own code, holding
                      TrafficMonitorApplication's main()):
                        - `com.example.schemas` — concrete message classes
                          (fruit/weather/ping/candy/rada).
                        - `com.example.messagehandlers` — concrete MessageArrivedHandler
                          implementations, one per message type, including
                          messagehandlers/rada/RadaTracksExtendedHandler.
                      Holds the spring-boot-maven-plugin config and the module's
                      integration-test suite.
traffic-tester-app   Standalone CLI tester, depends on traffic-monitor-app (for the message
                      classes — it's a test tool, allowed to know the wire format) +
                      Instancio for random payloads. See the exec-jar note below for why this
                      dependency resolves to plain classes rather than the fat Spring Boot jar.
```

Build/test a module + its deps: `mvn -pl <module> -am test`. Full repo: `mvn clean verify`
from the root. Integration tests (`*IT.java`, real Spring context + real sockets) run via
`failsafe`, bound to the `test` phase in traffic-monitor-app specifically (see the comment on
that module's failsafe execution — repackage/failsafe ordering gotcha below).

**Exec-jar classifier**: traffic-monitor-app's spring-boot-maven-plugin repackage execution
uses `<classifier>exec</classifier>`, so `mvn package` produces both
`traffic-monitor-app-<version>.jar` (plain classes, the resolvable Maven dependency
traffic-tester-app consumes) and `traffic-monitor-app-<version>-exec.jar` (the runnable fat
jar — nested `BOOT-INF/classes/...`, not consumable as a library). Without the classifier,
repackage replaces the main artifact in place with the fat jar, silently breaking any other
module that depends on this one for its plain classes. Run the app via the `-exec` jar (or
`mvn -pl traffic-monitor-app spring-boot:run`), not the plain one.

## Core architectural invariant: engine has zero schema dependency

`traffic-monitor-app-core` never imports `com.example.schemas.*` or `com.example.messagehandlers.*`
in main code — enforced by the pom (it has no dependency on traffic-monitor-app, the module
those packages now live in, at all, not even test-scope; see "IT suite lives in
traffic-monitor-app" below for why). All wiring from
generic engine to concrete protocol classes happens by fully-qualified class name string, read
from YAML config (`config/traffic-tool.yml`) and resolved via `Class.forName` at startup
(`MessageSchemaWiringConfig`). This means new protocols never require touching the engine.

## IT suite lives in traffic-monitor-app, not traffic-monitor-app-core

traffic-monitor-app-core's test Spring context boots the full app wiring (its trimmed
`TrafficMonitorTestApplication` only scans `com.example.monitor`, no handler packages), so it
can only host tests that don't need concrete message/handler classes on the classpath — plain
unit tests and Spring slice tests (`@WebMvcTest`, `@JdbcTest`). The real end-to-end integration
suite (`*IT.java`, real UDP/TCP sockets + real Spring context wired to real interfaces) lives in
`traffic-monitor-app` instead, which holds `com.example.schemas`/`com.example.messagehandlers`
directly and has a real bootable `TrafficMonitorApplication` (scanning `com.example.messagehandlers`
too) for the tests to boot against. Test config:
`traffic-monitor-app/src/test/resources/traffic-tool-test.yml` + `application.yml`.

## The reflective codec convention (traffic-monitor-app-core's com.example.schemacore)

Messages don't need a hand-written `MessageDefinition` + separate codec class pair anymore.
`ReflectiveStructCodec` reflectively invokes methods/constructors that follow a convention:

- **Decode** (first match wins): `public static T fromByteBuffer(ByteBuffer)` (used by
  records — immutable, can't self-mutate; the codec wraps the payload and applies the
  requested `ByteOrder` to the buffer before invoking, so these classes are automatically
  byte-order-aware with no class changes) — else `public T(byte[], ByteOrder)` constructor
  (order-aware mutable structs, e.g. the rada messages) — else `public T(byte[])` constructor
  (mutable classes that don't care about byte order; effectively fixed to whatever they
  hardcode internally).
- **Encode** (first match wins): `public byte[] toByteArray(ByteOrder)` (order-aware
  self-sizing) — else `public byte[] toByteArray()` no-arg, self-sizing (used when a field is
  variable-length, e.g. a `String` — `StructSizeCalculator` can't size those) — else
  `public void toByteArray(ByteBuffer)`, buffer pre-sized via
  `StructSizeCalculator.calculateStructSize(class)` and given the requested `ByteOrder` by the
  codec before invoking (used for fixed-layout messages; array fields need
  `@FixedArrayLength(n)` from `com.example.schemacore.annotation` for this to work).

`ReflectiveMessageDefinition(interfaceName, messageType, opcode, messageClass, byteOrder)`
wraps this into a `MessageDefinition` — one line of config replaces one hand-written Java
class. Config supports both `definitionClass:` (legacy hand-written) and
`messageClass:`+`opcode:` (reflective) per message entry; all current messages use the
reflective style. `byteOrder:` can be set per-interface (`InterfaceConfig`, default
`BIG_ENDIAN`) and/or per-message (`MessageConfig`, overrides the interface's value when set) -
resolved once at startup in `MessageSchemaWiringConfig.resolveByteOrder` and threaded through
to `ReflectiveStructCodec`. Header decoding (`MessageIngestionPipeline`/`TcpIngestionRunner`
decoding `headerType`) uses `InterfaceConfig.resolveByteOrder()` - the interface-level value
only, never a per-message override, since the header has to be parsed before the opcode (and
thus which message-level override applies) is even known. `InterfaceConfig.resolveByteOrder()`
/ the static `InterfaceConfig.parseByteOrder(String, String)` it delegates to is also what
`MessageSchemaWiringConfig.resolveByteOrder` calls for the message-level case, so there's one
place that turns a `byteOrder:` string into a `java.nio.ByteOrder` (and fails fast on anything
other than `BIG_ENDIAN`/`LITTLE_ENDIAN`). `traffic-tester-app`'s `UdpListener` has no
per-interface config to resolve from (it just decodes known legacy-envelope replies for
display), so it passes `ByteOrder.BIG_ENDIAN` explicitly instead - the legacy envelope is
always big-endian regardless.

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

TCP dedicated-port ingestion **is implemented** (`TcpIngestionRunner`, one `ServerSocket` per
enabled TCP interface) — Candy runs on it today. See "TCP client/server mode" below for the
one remaining ingestion-direction gap this used to have (client mode), which is now also filled.

Per-interface runtime start/stop (`/api/interfaces/{key}/start|stop`,
`InterfaceRuntimeRegistry`/`InterfaceControlService`) only applies to dedicated-port
interfaces. Legacy interfaces are all-or-nothing via `traffic.udp.enabled`/`traffic.tcp.enabled`.
`InterfaceControlService.isTcp()` dispatches `start`/`stop` to `TcpIngestionRunner` vs
`UdpIngestionRunner` based on the interface's *current* protocol (switchable at runtime via
`configure`).

## TCP client/server mode

Every TCP interface has a `mode`: `"SERVER"` (default — bind `port` and listen, as always) or
`"CLIENT"` (connect out to `host:port` instead, using the same decode pipeline once connected).
`mode`/`host` live on `InterfaceConfig` alongside `port`/`protocol`, validated together by
`InterfaceModeValidator` (shared between config-load time and runtime `/configure` calls):
`CLIENT` requires `protocol=TCP` (UDP is connectionless — no client/server distinction) and a
non-blank `host`. Client mode is UI/API-configurable per interface the same way protocol/port
already were (`InterfaceConfigureRequest`/`InterfaceStatusDto` both carry `mode`/`host`).

`TcpIngestionRunner.startInterface` branches on mode: `SERVER` binds synchronously and throws
on failure (unchanged); `CLIENT` never throws synchronously — it registers a stop flag and
starts a background reconnect loop (`connectLoopForInterface`) that retries every
`traffic.tcp.client-reconnect-delay-ms` (default 2s) with a bounded
`traffic.tcp.client-connect-timeout-ms` (default 3s) per attempt, since the remote may not be
up yet. Successful connections are handed to the exact same `handleConnectionForInterface`
server mode uses. `stopInterface`'s cleanup is shared across both modes via the existing
`dedicatedConnections` map/close loop, with one caveat: a client-mode connect attempt already
in flight has no live socket yet to force-close, so `stopInterface`'s worst-case latency for a
`CLIENT` interface is bounded by `client-connect-timeout-ms`, not instant.

## Config files

- `config/traffic-tool.yml` — the interfaces/messages/auto-reply config, loaded by
  `TrafficToolConfigLoader` (env var `TRAFFIC_TOOL_CONFIG`, default path
  `config/traffic-tool.yml` relative to CWD — run from repo root). This is where
  `messageClass:`/`definitionClass:`, dedicated ports, `headerType:`, broadcast targets, etc.
  live. Test equivalent: `traffic-monitor-app/src/test/resources/traffic-tool-test.yml`.
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
- **`ByteBuffer.order(...)` is a buffer property, not a per-call one**: rada's nested structs
  (`RadaHeader`, `RadaTrackData`, `RadaPlotData`) all share one `ByteBuffer` instance passed
  down from the top-level message's `fromByteArray`, so only the outermost entry point
  (the `T(byte[], ByteOrder)` constructor) may call `.order(...)` — a nested struct's own
  `fromByteArray` calling `.order(...)` again would silently clobber whatever order the caller
  set. This is why none of the rada `fromByteArray(ByteBuffer)` methods set order themselves
  anymore; they trust whatever order the buffer already has going in.
- **Instancio + `@FixedArrayLength`**: Instancio doesn't know about this project's custom
  annotation and will generate arrays of its own default length, which then mismatches what
  `StructSizeCalculator` allocates. `RadaTracksExtended` (array-heavy) is deliberately *not*
  wired into the tester app's Instancio generation for this reason — only `RadaStatus`
  (scalar-only) is. Fixing this needs explicit `Instancio.of(...).generate(field(...), gen ->
  gen.array().length(n))` per annotated array field.
- **`spring-boot-maven-plugin:repackage` running immediately before Failsafe in the same
  lifecycle pass breaks Spring's test-context bootstrapping**: in traffic-monitor-app,
  `mvn verify` (or any invocation where `package` and `integration-test` both run in one
  process) made every `*IT.java` fail with `IllegalStateException: Failed to find merged
  annotation for @BootstrapWith(SpringBootTestContextBootstrapper.class)` — reproducible even
  with zero Surefire tests in the module, and confirmed absent when Failsafe runs before
  `package`, or as a fully separate `mvn` invocation from repackage. Root cause not fully
  isolated (looks like repackage leaves some JVM-process-level state that corrupts annotation
  merging for Failsafe's forked test JVM), but the fix is straightforward: traffic-monitor-app's
  failsafe execution binds its `integration-test`/`verify` goals to the `test` phase instead of
  their defaults, so it completes before `package`/repackage ever runs. This never surfaced when
  the IT suite lived in traffic-monitor-app-core because that module has no
  spring-boot-maven-plugin at all.

## Known gaps / natural follow-ups

- `RadaTracksExtended` Instancio generation (see above).
- TCP client mode reconnects/backoff apply uniformly regardless of how many prior attempts
  failed (flat delay, no exponential backoff) — fine at today's scale (~5 interfaces), but
  would need revisiting if that count grows a lot.
- Multi-select interface filtering in the Live/History UI tabs — dropdowns are dynamic now
  (all 5 interfaces show up) but still single-select; true multi-select needs
  `HistoryController`/`AnalyticsController` to accept a repeatable `interfaceName` param.
- No standalone Spring-free ingestion library module or a second thin deployable app module
  for customer-specific handler bundles — that capability exists conceptually (extend
  `com.example.messagehandlers`-shaped classes) but isn't split into a separate reusable artifact;
  after the shared-schemas/handler-app merge, extracting one would mean pulling packages back
  out of traffic-monitor-app rather than depending on an existing standalone module.
- `traffic-tester-app`'s `PayloadFactory` always encodes rada payloads via the 2-arg
  `ReflectiveStructCodec.encode(message)` (implicit `BIG_ENDIAN`), so it doesn't respect
  message-level `byteOrder:` overrides in `config/traffic-tool.yml` — the tester and the
  monitor would silently disagree on wire format if a message's configured order were flipped.
  Not wired up because nothing in this repo currently needs a non-default order in practice;
  see the commented-out example on the `rada` interface's `RadaExtendedStatus` entry.
