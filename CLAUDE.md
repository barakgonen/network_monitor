# Traffic Interface Tool

Java 21 / Maven multi-module project for simulating and observing custom binary protocol
traffic over UDP and TCP. Two runnable apps: `traffic-monitor-app` (Spring Boot service that
ingests, stores, analyzes, and publishes protocol traffic, with a web UI) and
`traffic-tester-app` (CLI that sends synthetic traffic at the monitor).

**`README.md` is currently stale** (references deleted classes like `FruitProtocolCodec`,
says "seven modules" / "four protocols" — both are now wrong). Don't trust it for module
counts or class names; this file and the code are the source of truth. It should be
regenerated/updated at some point.

## Module graph (9 modules)

```
traffic-config        Plain config-loading classes with zero Spring/other dependency:
                      `TrafficToolConfig`/`InterfaceConfig`/`MessageConfig`/`TrafficToolConfigLoader`/
                      `InterfaceModeValidator` (package `com.example.trafficconfig`). Extracted from
                      traffic-monitor-app-core's `com.example.monitor.schema` so sample-publisher-app
                      could read `config/traffic-tool.yml` without depending on that module at all
                      (see "Standalone-module extraction" below). `MessageSchemaWiringConfig` (the
                      one Spring `@Configuration` class that used to live alongside these) stayed
                      behind in traffic-monitor-app-core — it's ingestion-wiring glue, not reusable
                      data — and now imports `TrafficToolConfig`/`InterfaceConfig` from here instead.
binary-serdes         The JSON-schema-driven binary protocol engine, zero Spring dependency, classic
                      Jackson 2 (`com.fasterxml.jackson.*`) rather than Jackson 3: `Protocol`/
                      `ProtocolIn`/`ProtocolOut`/`MessageType`/`MessageField`/`Type`/`RecordType`/
                      `ArrayType`/`Translator`+impls/`config.*` (package `com.example.binaryserdes`),
                      plus the legacy 16-byte envelope codec `ProtocolHeaderCodec`/`ProtocolHeader`
                      (`com.example.binaryserdes.envelope`). Extracted from traffic-monitor-app-core's
                      `com.example.schemacore.binaryserdes`/`.envelope`. The two adapter classes
                      bridging this engine to traffic-monitor-app-core's `MessageDefinition`/
                      `HeaderDecoder` abstractions (`SerdesMessageDefinition`/`SerdesHeaderDecoder`)
                      stayed behind, relocated to `com.example.monitor.schema` (their only
                      consumers) — this engine has no concept of `MessageDefinition` itself.
rest-schema           OpenAPI/Swagger discovery, zero Spring dependency: `RestSwaggerLoader`/
                      `RestSchemaConverter`/`RestSchemaNode`/`RestApiDefinitionBuilder`/
                      `RestOperationDefinition`/`RestApiDefinition`/`RestParameterDefinition`
                      (package `com.example.restschema`). Extracted from traffic-monitor-app-core's
                      `com.example.monitor.rest` — the ingestion/wiring-specific pieces
                      (`RestSchemaWiringConfig`/`RestOperationRouter`/`RestAutoReplySettingsService`)
                      stayed behind in `-core`, tied to REST ingestion.
traffic-monitor-app-core   The generic engine, plus what used to be two separate modules
                      (schema-core, handler-core) folded directly into it — merged because
                      handler-core and shared-schemas both compile-depended on schema-core, and
                      this module already compile-depended on handler-core, so schema-core
                      couldn't move here alone without a cycle. Package layout:
                        - `com.example.schemacore` (+ `.annotation`/`.envelope`/`.reflect`
                          sub-packages) — MessageDefinition/Registry, the reflective codec engine
                          (the legacy fixed envelope *codec* itself, `ProtocolHeaderCodec`, moved to
                          `binary-serdes`; `DefaultEnvelopeHeader`, a reflective-codec POJO, stayed).
                          Message classes are plain `Object`s — no marker interface — identified by
                          `Class<?>` and the reflective codec's method-naming convention only (see
                          below).
                        - `com.example.monitor` — the engine itself: ingestion, persistence,
                          analytics, interface runtime control, REST API,
                          UI resources. Includes `.rest` (+ `.ingestion.rest`) — the dynamic,
                          no-codegen OpenAPI/Swagger-driven REST interface support (see "REST
                          interfaces" below), now built on the `rest-schema` module's discovery
                          classes; unlike everything else here, this sub-area's own end-to-end IT
                          suite lives in *this* module, not traffic-monitor-app.
                      Depends on all three modules above (`traffic-config`/`binary-serdes`/
                      `rest-schema`) for the pieces that moved out — see "Standalone-module
                      extraction" below. Has zero compile dependency on shared-schemas/handler-app
                      (see invariant below) — its own test tree mostly holds pure unit/slice tests
                      (the real end-to-end integration-test suite for UDP/TCP lives in
                      traffic-monitor-app instead), except for REST's own IT suite (see "IT suite
                      lives in traffic-monitor-app... (except REST)" below).
traffic-monitor-app  The runnable app, and also what used to be two more separate modules
                      (shared-schemas, handler-app) folded directly into it — merged the same
                      way, since neither has any other consumer besides this module and
                      traffic-tester-app (which now depends on this module instead; see the
                      exec-jar note below). Package layout adds two more top-level packages
                      alongside `com.example.monitor` (the app's own code, holding
                      TrafficMonitorApplication's main()):
                        - `com.example.schemas` — concrete message classes
                          (fruit/weather/ping/candy/rada).
                      Holds the spring-boot-maven-plugin config and the module's
                      integration-test suite.
traffic-tester-app   Standalone CLI tester, depends on traffic-monitor-app (for the message
                      classes — it's a test tool, allowed to know the wire format) + `binary-serdes`
                      (directly — `PayloadFactory`/`KnownMessageDecoder` use the envelope codec) +
                      Instancio for random payloads. See the exec-jar note below for why the
                      traffic-monitor-app dependency resolves to plain classes rather than the fat
                      Spring Boot jar.
traffic-destination-app   Standalone reply-simulator app, depends on traffic-monitor-app + `binary-serdes`
                      directly (`GreetingReplyEncoder`/`PongReplyEncoder` use the serdes engine and
                      envelope codec to build protocol-correct replies). See "Auto-reply lives in
                      traffic-destination-app, not here" below.
traffic-proxy-app    Standalone UDP/TCP relay app sitting between traffic-tester-app and
                      traffic-destination-app; only touches binary-serdes-era classes in its own
                      test sources (via test-scoped deps on the two apps above).
sample-publisher-app Standalone Spring Boot app (package `com.example.publisher`) for sending
                      UDP/TCP/REST test traffic — depends on **only** `traffic-config`/
                      `binary-serdes`/`rest-schema`, zero dependency on traffic-monitor-app/-core.
                      See "Publishing lives in sample-publisher-app, not here" below.
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

## serdes-generator (not shown in the module graph above — pre-existing doc gap)

A 10th module, `serdes-generator` (package `com.example.serdesgenerator`), is a dev-time CLI
(`GeneratorMain --manifest <manifest.yml> --output <output.protocol.json>`) that reflects over
fixed-layout Java message classes (the ones `StructSizeCalculator`/`ReflectiveStructCodec` can
size/codec — see "The reflective codec convention" below) and generates the equivalent
`binary-serdes` `*.protocol.json`, so a class's wire format doesn't need hand-authoring twice.
It resolves each manifest entry's `className:` purely via `Class.forName` at runtime — no
compile-time dependency on any schema module, deliberately: this generator is meant to be reused
against schema classes it has never heard of (e.g. `generator/rada.manifest.yml` currently points
at traffic-tester-app's rada classes, but nothing here is rada-specific).

**Same classifier pattern as the exec-jar note above, via `maven-shade-plugin` instead of
spring-boot-maven-plugin**: `mvn package` produces `serdes-generator-<version>.jar` (plain
classes — the resolvable Maven dependency, e.g. for an external "offline-generator" project that
depends on this jar plus its own separate schemas jar) and
`serdes-generator-<version>-standalone.jar` (classifier `standalone`, a single self-contained
uber jar bundling every runtime dependency — including traffic-monitor-app-core's full Spring
Boot transitive tree, accepted as a non-issue for a dev-time CLI never deployed as a service).
The standalone jar is the one meant for airgapped environments: `java -jar
serdes-generator-<version>-standalone.jar --manifest ... --output ...` needs nothing else on
disk except whatever jar(s) supply the manifest's schema classes, layered on top via `-cp`
alongside it (see `generator/rada.manifest.yml`'s usage comment for a concrete example).

`serdes-generator` has no dependency (compile or test) on traffic-tester-app or any other schema
module. Its strongest correctness test, `RadaRoundTripTest` (encodes a real rada message via
`ReflectiveStructCodec`, decodes the same bytes via a generator-produced `protocol.json`, asserts
every field round-trips), needs traffic-tester-app's actual rada classes, so that test lives in
`traffic-tester-app`'s own test tree instead — a test-scope-only dependency on `serdes-generator`
in the other direction, avoiding a reactor cycle.

## Standalone-module extraction (traffic-config / binary-serdes / rest-schema)

These three modules exist for one reason: `sample-publisher-app` needed to read `config/traffic-tool.yml`,
encode/decode binary protocol messages, and discover REST operations from swagger files, without
depending on `traffic-monitor-app-core` — which drags in ingestion/persistence/auto-reply as unused
transitive weight and defeats the point of `sample-publisher-app` being a truly standalone app (see
"Publishing lives in sample-publisher-app, not here" below). All three are plain Java, zero Spring
dependency — traffic-monitor-app-core and sample-publisher-app each wire their classes with their own
explicit `@Bean` methods.

The split rule throughout: **pure data/discovery/codec logic moved out; Spring wiring glue and
ingestion-specific machinery stayed in traffic-monitor-app-core.** Concretely, `MessageSchemaWiringConfig`/
`RestSchemaWiringConfig` (the `@Configuration` classes), `SerdesMessageDefinition`/`SerdesHeaderDecoder`
(the adapters bridging `binary-serdes` to traffic-monitor-app-core's own `MessageDefinition`/`HeaderDecoder`
interfaces), and `RestOperationRouter`/`RestAutoReplySettingsService` (REST ingestion/auto-reply)
all stayed behind — none of them are things sample-publisher-app needs or should depend on.

One consequence: `traffic-destination-app`/`traffic-tester-app` — which never asked to be part of this
split — turned out to import `binary-serdes` classes (`Protocol`/`ProtocolIn`/`ProtocolOut`/
`ProtocolHeaderCodec`) directly already, reached transitively through their existing dependency on
traffic-monitor-app/-core. Once those classes physically moved modules, both apps needed their own
explicit `binary-serdes` dependency + updated imports (mechanical, no behavior change) — a reminder that
"which module actually declares a dependency" and "which module's classes does my code literally import"
can silently diverge over a project's life, and only surfaces at compile time when something moves.

`sample-publisher-app`'s own `SendOrchestrationService.sendSerdes` also had to change, not just its
imports: it used to encode via `MessageDefinitionRegistry.find(...).encodeBody(...)`, but
`MessageDefinition`/`MessageDefinitionRegistry`/`SerdesMessageDefinition` are traffic-monitor-app-core-only
abstractions this app has no access to anymore. It now works one layer down — `ProtocolOut.encodeInto`
directly (see `SendOrchestrationService.encodeSerdesBody`, which duplicates
`SerdesMessageDefinition.encodeBody`'s "over-allocate a buffer, trim after" trick for variable-length
fields, since `ProtocolOut.encode(...)`'s auto-sized buffer overload throws on those). Similarly, the
5 send-mechanics classes (`UdpMessagePublisher`/`TcpMessagePublisher`/`RestOperationInvoker`/
`RestInvocationResult`/`TransportSelector`) and the field-path utility `FlattenedFieldPathUtil` are
**copied** into `com.example.publisher.send.io`/`com.example.publisher.util` rather than depended on —
they're tiny, and the alternative (a fourth extraction module, or leaving sample-publisher-app dependent
on traffic-monitor-app-core just for these) wasn't worth it. The copied `Udp`/`TcpMessagePublisher` drop
their `MeterRegistry` metrics parameter (this app exposes no `/actuator/prometheus` endpoint, so the
counters were never observable here).

## Core architectural invariant: engine has zero schema dependency

`traffic-monitor-app-core` never imports `com.example.schemas.*`
in main code — enforced by the pom (it has no dependency on traffic-monitor-app, the module
that package now lives in, at all, not even test-scope; see "IT suite lives in
traffic-monitor-app" below for why). All wiring from
generic engine to concrete protocol classes happens by fully-qualified class name string, read
from YAML config (`config/traffic-tool.yml`) and resolved via `Class.forName` at startup
(`MessageSchemaWiringConfig`). This means new protocols never require touching the engine.

## IT suite lives in traffic-monitor-app, not traffic-monitor-app-core (except REST)

traffic-monitor-app-core's test Spring context boots the full app wiring (its trimmed
`TrafficMonitorTestApplication` only scans `com.example.monitor`), so it
can only host tests that don't need concrete message classes on the classpath — plain
unit tests and Spring slice tests (`@WebMvcTest`, `@JdbcTest`). The real end-to-end integration
suite (`*IT.java`, real UDP/TCP sockets + real Spring context wired to real interfaces) lives in
`traffic-monitor-app` instead, which holds `com.example.schemas`
directly and has a real bootable `TrafficMonitorApplication` for the tests to boot against. Test config:
`traffic-monitor-app/src/test/resources/traffic-tool-test.yml` + `application.yml`.

**Exception: REST ITs** (`RestServerIngestionIT`, `RestClientPublishingIT`) live in
traffic-monitor-app-core instead, under `com.example.monitor.rest`. The reason for the split above
doesn't apply to REST — a REST interface needs no concrete schema/handler class at all (fully
dynamic, no codegen), so `TrafficMonitorTestApplication` can boot one just fine. This required
adding a `<build><plugins><plugin>maven-failsafe-plugin</plugin>` block to
`traffic-monitor-app-core/pom.xml` (previously absent — that module relied only on the root
pom's `pluginManagement` defaults for failsafe, which configure the plugin *if* referenced but
never invoke it on their own), binding to failsafe's default `integration-test`/`verify` phases —
no `test`-phase workaround needed, since this module has no `spring-boot-maven-plugin` to clash
with (see the repackage-ordering gotcha below). Fixture config lives inline in each IT via
`@DynamicPropertySource` (a temp YAML file with a freshly-chosen free port), plus
`src/test/resources/rest/sample-openapi.yml` — deliberately separate from the demo
`swagger/pets-demo.yml` at the repo root, so these tests don't depend on that file's contents.

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

**No marker interface required.** `messageClass:` in config just needs a class following the
convention above — it doesn't need to implement anything this project defines. This is
deliberate: message classes can come from an external dependency (e.g. a client's own schema
library) that this project doesn't get to modify, and previously requiring `implements
ProtocolMessage` would have forced a compile-time dependency back onto this engine just to be
wire-compatible with it. `MessageDefinition`/`ReflectiveMessageDefinition` are typed on
`Class<?>`/`Object` throughout for this reason (there used to be a `ProtocolMessage` marker
interface; it added no behavior and was removed). Since there's no interface to lean on for
fail-fast validation, `MessageSchemaWiringConfig.resolveDefinition` instead calls
`ReflectiveStructCodec.requireDecodable`/`requireEncodable` right after `Class.forName(...)` —
these check (without needing an instance) that the class actually exposes one of the recognized
decode/encode shapes above, so a shape mismatch still fails at startup instead of on the first
real message.

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

Corollary for anything encoding a message to send (sample-publisher-app's
`SendOrchestrationService`, the only place this happens now that publishing moved out of this
module): legacy interfaces need `definition.encodeBody(...)` wrapped in
`ProtocolHeaderCodec.encodeMessage(opcode, ts, body)`; dedicated-port interfaces send
`definition.encodeBody(...)` as-is (already includes the header). Branch on
`InterfaceConfig.isMessageOwnsHeader()`.

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

## Auto-reply lives in traffic-destination-app, not here

`traffic-monitor-app` is a UDP/TCP/REST **viewer** — it ingests, stores, analyzes, and publishes
traffic, but (aside from REST's mandatory synchronous response, see below) it never replies to
inbound traffic on its own. It used to have a generic, config-driven handler-based auto-reply
mechanism (`com.example.handlercore` — `MessageArrivedHandler`/`MessageHandlerRegistry`/
`MessageArrivedDispatcher`/`ReplySender`, plus `com.example.monitor.autoreply.AutoReplySettingsService`
and an `/api/autoreply/*` REST API + UI panel), but it was fully dead by the time it was removed —
every `autoReply.enabled` in config was `false`, and the `MessageArrivedHandler` implementations it
would have dispatched to (`com.example.messagehandlers`) had already been deleted in an earlier
session. It was deleted outright rather than kept as unused scaffolding.

Real reply behavior (echoing, or building a protocol-correct response like Ping→Pong) now lives in
`traffic-destination-app` instead — see that module's `ReplyMode` (`NONE`/`ECHO`/`PONG`/`GREETING`)
in `config/destination-interfaces.yml`. That mechanism predates this reframing (it was built to make
`traffic-destination-app` a believable backend sink for `traffic-proxy-app` to relay to) but is now
this project's auto-reply story going forward; there is no plan to rebuild reply capability into
`traffic-monitor-app`.

REST is the one exception: a REST server must return *some* HTTP response by protocol necessity, so
`RestAutoReplySettingsService` (see "REST interfaces" below) is untouched by this — it's not an
optional add-on the way the deleted UDP/TCP mechanism was. Unlike that mechanism, though, REST's
version was never user-configurable to begin with: the old "REST Auto-Reply" UI tab that let you
override the response per operation was itself removed (along with `RestAutoReplyController`/
`RestOperationsController` and their DTOs) since it was a configuration nicety on top of the
mandatory response, not the mandatory response itself. `RestAutoReplySettingsService.resolve()`
now unconditionally derives the response from the OpenAPI spec (example, or a synthesized
placeholder) — see "REST interfaces" below.

## Publishing lives in sample-publisher-app, not here

`traffic-monitor-app` sends nothing of its own choosing — the same "viewer" framing as auto-reply
above. It used to have three ways to send test traffic (all in `index.html`'s "Sample Publisher"
tab, backed by `com.example.monitor.publisher`/parts of `com.example.monitor.publishing`/`com.example.monitor.api`):
a legacy hardcoded fruit/weather-only form with periodic send, a reflection-based "Generic
Publisher" that worked for any interface via `Class.forName`, and a "REST Publisher" card. The
Generic Publisher was fully broken by the time it was removed: every UDP/TCP interface had already
migrated to the JSON-schema-driven binary-serdes engine (`com.example.schemacore.binaryserdes` at the
time; now the standalone `binary-serdes` module, see "Standalone-module extraction" above), where
messages have **no backing `Class<?>` at all** (`SerdesMessageDefinition.messageClass()` deliberately
returns `null`) — so `PublisherFieldMetadataService.describeFields(Class<?>)` had nothing to reflect
on, and the UI sent the literal string `"null"` as a query param, crashing with
`ClassNotFoundException: null` on every message. Rather than patch this in place, the whole publishing
concern was extracted into its own module.

All of it now lives in **`sample-publisher-app`** (package root `com.example.publisher`) — a separate
Spring Boot app with its own static UI, depending on **only** `traffic-config`/`binary-serdes`/
`rest-schema` (not `traffic-monitor-app`/`-core` at all — see "Standalone-module extraction" above)
and reading the *same* `config/traffic-tool.yml`. It describes UDP/TCP messages by walking the serdes
`MessageType`/`Type`/`RecordType`/`ArrayType` tree directly
(`com.example.publisher.serdes.SerdesFieldMetadataService`/`SerdesRequestBodyAssembler`, mirroring
`RestFieldMetadataService`/`RestRequestBodyAssembler`'s shape but for the serdes type tree instead
of an OpenAPI schema) rather than reflecting a `Class<?>`, so it works for every current interface.
REST operations reuse copies of `RestFieldMetadataService`/`RestRequestBodyAssembler` under
`com.example.publisher.rest` (copied, not depended on, same reasoning as the send-mechanics classes
in "Standalone-module extraction" above — `RestSchemaNode` now comes from the `rest-schema` module).
Config wiring builds its own `@Bean`s directly from `traffic-config`/`rest-schema`/`binary-serdes`'s
plain classes (`TrafficConfigBeans`/`RestSchemaBeans`/`SerdesEngineBeans` — the latter exposing both
`Map<String, List<MessageType>>` for field description and `Map<String, ProtocolOut>` for encoding)
rather than `@ComponentScan`-ning `com.example.monitor` (which would also pull in
ingestion/persistence/auto-reply machinery this app has no business booting, and no longer even
resolves on this app's classpath at all).

Unlike the deleted `PublisherService`, sample-publisher-app has **no ingestion/storage of its
own** — a REST response is returned directly to the HTTP caller, not captured as a newly-observed
message anywhere. It also generalizes periodic sending (the old legacy card's only feature) to
every UDP/TCP message and REST operation via `PeriodicSchedulerService`, supporting multiple
concurrent jobs (not just one global slot) keyed by a generated `jobId`.

`traffic-monitor-app-core` kept a small `InterfaceCatalogController`/`InterfaceCatalogService`
(`GET /api/interfaces/catalog`) as a direct replacement for what `/api/publisher/interfaces` used
to also serve besides publishing: the viewer's own sidebar filter chips and History tab's interface
dropdown. It's built from `TrafficToolConfig` + the existing `interfaceMessageDefinitionRegistries`
bean and never touches `messageClass()`/reflection at all.

## REST interfaces (dynamic, no codegen)

`protocol: REST` interfaces are driven entirely by an OpenAPI/Swagger YAML file (`swaggerFile:`
on `InterfaceConfig`, path relative to CWD like `config/traffic-tool.yml` itself, conventionally
under the repo-root `swagger/` directory) — parsed at startup (`io.swagger.parser.v3:swagger-parser`,
declared in the standalone `rest-schema` module's own pom, see "Standalone-module extraction" above;
traffic-monitor-app-core gets the `io.swagger.v3.oas.models.OpenAPI` type it needs transitively via
its dependency on `rest-schema` rather than declaring the parser directly) with zero Java code
required per new API, unlike UDP/TCP where a message still needs a hand-written/reflective-codec-compatible
class. This was a deliberate choice: dropping in a new swagger file is a restart, not a rebuild.

Because REST messages have no backing `Class<?>`, they're a **parallel universe** alongside
`com.example.schemacore` rather than plugging into it. The pure discovery classes
(`RestSchemaNode`/`RestSchemaConverter`/`RestOperationDefinition`/`RestApiDefinition`/
`RestApiDefinitionBuilder`/`RestSwaggerLoader`/`RestParameterDefinition`) live in the standalone
`rest-schema` module (`com.example.restschema`, no Spring dependency); the ingestion/wiring-specific
code (`RestSchemaWiringConfig`/`RestOperationRouter`/`RestAutoReplySettingsService`/etc.) stays in
`com.example.monitor.rest` (+ `com.example.monitor.ingestion.rest`) here, keyed by `operationId`
instead of opcode/`Class<?>`:

- `RestSchemaNode` — the `Schema`-walking analogue of a Java field tree (built by
  `RestSchemaConverter`, with a `MAX_DEPTH` guard — more important here than for a fixed Java
  class tree, since OpenAPI schemas can genuinely self-reference).
- `RestOperationDefinition`/`RestApiDefinition` — one per discovered operation/per interface,
  built by `RestApiDefinitionBuilder` walking the parsed `OpenAPI` model (JSON request/response
  media types only; other content types are skipped with a startup warning). Auto-discovered —
  there's no `messages:` list to hand-declare, unlike UDP/TCP.
- `RestSchemaWiringConfig` (stays in traffic-monitor-app-core) — the REST analogue of
  `MessageSchemaWiringConfig.interfaceMessageDefinitionRegistries`: a `@Bean Map<String, RestApiDefinition>
  restApiDefinitions`, one entry per REST interface, built from `rest-schema`'s `RestSwaggerLoader`/
  `RestApiDefinitionBuilder` (explicitly `@Bean`-wired here too, since those classes lost their
  `@Component` annotation when they moved to a Spring-free module). Same
  `@Qualifier("restApiDefinitions")` requirement as that other map bean (see the `Map<String, X>`
  gotcha below). `sample-publisher-app` inlines the same discovery loop in its own `RestSchemaBeans`
  rather than depending on this class, since it can't depend on traffic-monitor-app-core at all.
- The dotted/indexed flattened-path parsing (`unflatten`/`trackData[0].id`-style keys) lives in
  `com.example.schemacore.reflect.FlattenedFieldPathUtil`, used by `ReflectiveFieldApplier` here.
  `sample-publisher-app` has its own copy at `com.example.publisher.util.FlattenedFieldPathUtil`
  (same reasoning as the send-mechanics classes in "Standalone-module extraction" above — it's
  pure `Map`/`String` manipulation despite the original's package name, small enough to copy rather
  than justify a new module). `RestFieldMetadataService`/`RestRequestBodyAssembler` themselves moved
  to sample-publisher-app (see "Publishing lives in sample-publisher-app, not here" above) — they
  were publish-only, REST ingestion never used them.
- `RestIngestionRunner` (`SERVER` mode) — mirrors `TcpIngestionRunner`'s one-dedicated-socket-per-interface
  pattern, but using the JDK's built-in `com.sun.net.httpserver.HttpServer` (no new dependency)
  instead of a raw `ServerSocket`, since HTTP framing is the server's job, not ours — considerably
  simpler than `TcpIngestionRunner`, with no per-connection accept loop to run. `RestOperationRouter`
  matches incoming method+path against discovered operations (path templates compiled to `Pattern`s
  with positional, not named, capture groups — OpenAPI path param names can contain characters
  Java's named-group syntax rejects). `mode: CLIENT` is a deliberate **no-op** here (unlike TCP
  client mode, which still runs a background reconnect loop) — REST client mode has no persistent
  connection/server concept at all.
- `MessageIngestionPipeline.ingestRestOperation` — the REST entry point, sitting alongside
  `ingestForInterface`. Skips `decodeForInterface` entirely (the JSON body is already a
  `Map<String,Object>` via Jackson) and reuses only the shared store+archive tail
  (`storeAndArchive`, extracted out of `finishIngest` for this purpose).
- `RestAutoReplySettingsService` — REST server mode's "auto-reply" is a **mandatory** synchronous
  HTTP response (every request gets *some* response, by necessity of the protocol) — the only
  auto-reply mechanism traffic-monitor-app has (the old UDP/TCP handler-based auto-reply was
  removed; see "Auto-reply lives in traffic-destination-app, not here" below). `resolve()`
  unconditionally derives the response from the OpenAPI spec's own response schema: its
  `example` if present, else a synthesized placeholder instance (`""`/`0`/`false`/`[]`/recursive
  `{}` per leaf type). It used to also be a per-`(interfaceKey, operationId)` settings store with
  a user-configurable override (`update()`/`configuredValue()`, backing a "REST Auto-Reply" UI
  tab) — that override capability was removed as an unwanted configuration nicety, along with
  `RestAutoReplyController`, `RestOperationsController`, and their DTOs (`RestAutoReplyConfig`/
  `RestInterfaceDto`/`RestOperationSummaryDto`); the mandatory spec-derived fallback is the only
  behavior left.
- `RestOperationInvoker` — REST client-mode/on-demand publishing, using the JDK's built-in
  `java.net.http.HttpClient` (no new dependency). Moved to sample-publisher-app along with the rest
  of publishing (see above) — unlike UDP/TCP's fire-and-forget send, the whole point is the
  response, which that app now returns directly to its own caller (it has no ingestion of its own
  to capture it into, unlike the deleted `PublisherService.sendRest`).
- UI: sending REST traffic (the old "REST Publisher" card) is sample-publisher-app's own UI now.
  There is no REST auto-reply UI in traffic-monitor-app anymore.

`http://` only in v1 (no HTTPS config surface); `oneOf`/`anyOf` schemas collapse to their first
alternative for form-rendering (`RestSchemaConverter.firstAlternative`) rather than fully modeling
polymorphic bodies.

## Config files

- `config/traffic-tool.yml` — the interfaces/messages config, loaded by
  `TrafficToolConfigLoader` (env var `TRAFFIC_TOOL_CONFIG`, default path
  `config/traffic-tool.yml` relative to CWD — run from repo root). This is where
  `messageClass:`/`definitionClass:`, dedicated ports, `headerType:`, broadcast targets,
  `swaggerFile:`, etc. live. Test equivalent: `traffic-monitor-app/src/test/resources/traffic-tool-test.yml`.
- `swagger/` — OpenAPI/Swagger YAML files for `protocol: REST` interfaces, referenced by
  `swaggerFile:` (see "REST interfaces" above). `swagger/pets-demo.yml` is a demo spec proving out
  the dedicated-port REST path, wired up as the `pets` interface, the same role `rada` plays for
  the dedicated-port UDP path.
- `traffic-monitor-app-core/src/main/resources/application.yml` — Spring config: server port,
  H2 datasource, `traffic.udp`/`traffic.tcp`/`traffic.store` (legacy fixed-port settings).
- `config/tester-scenario.yml` — traffic-tester-app's scenario definition (what to send, how
  often, to which target).

## Interfaces currently configured

Fruit (Orange, Banana — legacy envelope), Weather (TemperatureReading — legacy envelope), Ping
(Ping, Pong — legacy envelope), Candy (Candy — legacy envelope), Rada (RadaStatus,
RadaExtendedStatus, RadaExtendedStatusMrs, RadaTracksExtended — dedicated port 5050, custom
`RadaHeader`, sample/demo radar-style protocol used to prove out the dedicated-port path), Rada
Little-Endian (`rada-le`, dedicated port 5051, RadaExtendedStatus only, `byteOrder:
LITTLE_ENDIAN` — demonstrates the same message class decoding under a different
interface-level byte order; see "Per-message byteOrder only works for legacy envelope
interfaces" below for why this had to be interface-level rather than a per-message override
sharing rada's port), Pets (`pets`, `protocol: REST`, dedicated port 5060, `swagger/pets-demo.yml`
— `getPet`/`createPet` operations, demo REST-over-swagger interface used to prove out the
dynamic REST path the same way `rada` proves out the dedicated-port UDP path).

### Per-message `byteOrder:` only works for legacy envelope interfaces

For `messageOwnsHeader: true` interfaces (rada-style), the ingestion pipeline has to peek the
header (`ReflectiveStructCodec.decode(headerType, headerBytes, interfaceConfig.resolveByteOrder())`
in `MessageIngestionPipeline`/`TcpIngestionRunner`) to read the opcode and route to the right
message class *before* it knows the message type — so that peek can only ever use the
interface's own default byte order, never a per-message override (there's no way to know an
override applies until after the very read it would need to affect). Concretely: `rada-le`'s
`RadaExtendedStatus` couldn't share `rada`'s port with a `byteOrder: LITTLE_ENDIAN` override on
just that message — the header peek would misread `msgType` itself (confirmed by hand: opcode 1
sent little-endian read back as `16777216` under the interface's big-endian default) and the
message would never reach the message-specific decode logic at all. Per-message overrides work
correctly (and are unit/wiring-tested, see `MessageSchemaWiringConfigTest`) for legacy envelope
interfaces instead, where the header is a separate, always-big-endian fixed struct
(`ProtocolHeaderCodec`) decoded independently of the body via its own buffer — a body-only
override there never touches header routing. If a `messageOwnsHeader` interface genuinely needs
mixed byte orders, split it into multiple interfaces (one dedicated port each), like
`rada`/`rada-le`, rather than reaching for the message-level override.

### Same message class registered on two interfaces (`rada`/`rada-le`)

`rada` and `rada-le` both wire up `com.example.schemas.rada.messages.RadaExtendedStatus` at
opcode 1. Per-interface *scoped* registries (`interfaceMessageDefinitionRegistries`, what
ingestion actually decodes against, and what sample-publisher-app's send path uses too) handle
this fine — each interface gets its own isolated registry. The flat, cross-interface
`messageDefinitionRegistry` bean can't: its opcode/class-keyed maps require global uniqueness, by
design (`MessageDefinitionRegistryTest` deliberately asserts duplicates throw — this catches real
config typos, like copy-pasting an interface block and forgetting to bump an opcode). Rather than
relaxing that invariant, `MessageSchemaWiringConfig.messageDefinitionRegistry` silently excludes a
later interface's definition from this *flat view only* when its opcode or message class was
already claimed by an earlier interface — `rada` (declared first) wins. This flat bean currently
has no consumer at all (its only use - "encode by opcode"/"encode by message class" for the
now-deleted legacy Sample Publisher - went away with the rest of publishing; see "Publishing lives
in sample-publisher-app, not here" above) but is kept rather than deleted, since
`MessageDefinitionRegistry`'s duplicate-detection invariant is independently useful/tested and a
future flat-view consumer may want it again.

## Gotchas learned the hard way

- **Spring `Map<String, X>` bean injection**: if you declare `@Bean Map<String, X>` yourself
  AND other beans of type `X` also exist in the context, plain `Map<String, X>`
  constructor-injection silently gets Spring's *implicit* "collect all beans of type X keyed
  by bean name" behavior instead of your explicit bean — your keys get replaced by bean names
  and your entries vanish. Fix: `@Qualifier("yourBeanName")` on the injection point. Bit us in
  `UdpIngestionRunner` with `interfaceMessageDefinitionRegistries` (and again in
  sample-publisher-app's own services depending on the same map, and `restApiDefinitions`).
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
  their defaults, so it completes before `package`/repackage ever runs. This doesn't surface for
  traffic-monitor-app-core's own REST ITs (see "IT suite lives in traffic-monitor-app... (except
  REST)" above) because that module has no spring-boot-maven-plugin at all, so its failsafe
  execution can safely use the default phases unmodified.
- **`cond ? Long.parseLong(...) : Integer.parseInt(...)` silently returns `Long` always** — Java's
  conditional-expression numeric promotion widens the `int` branch to `long` (binary numeric
  promotion of the two operand types) regardless of which branch actually executes at runtime, so
  a ternary mixing primitive `long`/`int` results autoboxes to `Long` unconditionally. Bit
  `RestRequestBodyAssembler.coerceScalar` (now in sample-publisher-app) coercing an OpenAPI
  `integer`+`format: int32` field — every "integer" field came out as `Long`, not just the
  `int64` ones. Fix: explicit boxed `if`/`yield` branches in the switch expression, not a ternary
  mixing primitive numeric types. Caught by an actual assertion failure
  (`expected: 3, but was: 3L`), not by inspection. Same gotcha applies to
  sample-publisher-app's `SerdesRequestBodyAssembler.coerceScalar`, which mirrors this pattern.

## Known gaps / natural follow-ups

- `RadaTracksExtended` Instancio generation (see above).
- **No HTTPS for REST** — sample-publisher-app's `RestOperationInvoker` only builds `http://`
  URIs; there's no TLS config surface for REST client-mode targets.
- **`oneOf`/`anyOf` OpenAPI schemas collapse to their first alternative** (`RestSchemaConverter.firstAlternative`)
  rather than fully modeling a polymorphic request/response body.
- **Switching a UDP/TCP interface to `protocol: REST` at runtime (via the Interfaces tab) doesn't
  actually work** — `swaggerFile:` isn't part of `InterfaceConfigureRequest`, and even if it were,
  `restApiDefinitions` is built once at Spring context startup from the interfaces already
  `protocol: REST` in config, not rebuilt on reconfigure. The protocol dropdown still lists REST
  (needed so a REST interface's *own* row displays correctly), but only interfaces already
  declared `protocol: REST` in `traffic-tool.yml` from startup are actually functional — unlike
  UDP↔TCP switching, which fully works at runtime.
- TCP client mode reconnects/backoff apply uniformly regardless of how many prior attempts
  failed (flat delay, no exponential backoff) — fine at today's scale (~5 interfaces), but
  would need revisiting if that count grows a lot.
- Multi-select interface filtering in the Live/History UI tabs — dropdowns are dynamic now
  (all 5 interfaces show up) but still single-select; true multi-select needs
  `HistoryController`/`AnalyticsController` to accept a repeatable `interfaceName` param.
- `traffic-tester-app`'s `PayloadFactory` always encodes rada payloads via the 2-arg
  `ReflectiveStructCodec.encode(message)` (implicit `BIG_ENDIAN`), so it doesn't respect
  message-level `byteOrder:` overrides in `config/traffic-tool.yml` — the tester and the
  monitor would silently disagree on wire format if a message's configured order were flipped.
  Not wired up because nothing in this repo currently needs a non-default order in practice;
  see the commented-out example on the `rada` interface's `RadaExtendedStatus` entry.
