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
                          sub-packages) — MessageDefinition/Registry, the legacy fixed envelope
                          codec, the reflective codec engine. Message classes are plain
                          `Object`s — no marker interface — identified by `Class<?>` and the
                          reflective codec's method-naming convention only (see below).
                        - `com.example.handlercore` — MessageArrivedHandler<T>,
                          MessageHandlerRegistry, MessageArrivedDispatcher, ReplySender,
                          DestinationConfig.
                        - `com.example.monitor` — the engine itself: ingestion, persistence,
                          analytics, auto-reply, publisher, interface runtime control, REST API,
                          UI resources. Includes `.rest` (+ `.ingestion.rest`) — the dynamic,
                          no-codegen OpenAPI/Swagger-driven REST interface support (see "REST
                          interfaces" below); unlike everything else here, this sub-area's own
                          end-to-end IT suite lives in *this* module, not traffic-monitor-app.
                      Has zero compile dependency on shared-schemas/handler-app (see invariant
                      below) — its own test tree mostly holds pure unit/slice tests (the real
                      end-to-end integration-test suite for UDP/TCP lives in traffic-monitor-app
                      instead), except for REST's own IT suite (see "IT suite lives in
                      traffic-monitor-app... (except REST)" below).
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

## IT suite lives in traffic-monitor-app, not traffic-monitor-app-core (except REST)

traffic-monitor-app-core's test Spring context boots the full app wiring (its trimmed
`TrafficMonitorTestApplication` only scans `com.example.monitor`, no handler packages), so it
can only host tests that don't need concrete message/handler classes on the classpath — plain
unit tests and Spring slice tests (`@WebMvcTest`, `@JdbcTest`). The real end-to-end integration
suite (`*IT.java`, real UDP/TCP sockets + real Spring context wired to real interfaces) lives in
`traffic-monitor-app` instead, which holds `com.example.schemas`/`com.example.messagehandlers`
directly and has a real bootable `TrafficMonitorApplication` (scanning `com.example.messagehandlers`
too) for the tests to boot against. Test config:
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

## REST interfaces (dynamic, no codegen)

`protocol: REST` interfaces are driven entirely by an OpenAPI/Swagger YAML file (`swaggerFile:`
on `InterfaceConfig`, path relative to CWD like `config/traffic-tool.yml` itself, conventionally
under the repo-root `swagger/` directory) — parsed at startup (`io.swagger.parser.v3:swagger-parser`,
new dependency in `traffic-monitor-app-core/pom.xml`) with zero Java code required per new API,
unlike UDP/TCP where a message still needs a hand-written/reflective-codec-compatible class. This
was a deliberate choice: dropping in a new swagger file is a restart, not a rebuild.

Because REST messages have no backing `Class<?>`, they're a **parallel universe** alongside
`com.example.schemacore`/`com.example.handlercore` rather than plugging into either — all new code
lives in `com.example.monitor.rest` (+ `com.example.monitor.ingestion.rest`), keyed by
`operationId` instead of opcode/`Class<?>`:

- `RestSchemaNode` — the `Schema`-walking analogue of a Java field tree (built by
  `RestSchemaConverter`, same recursion shape and `MAX_DEPTH` guard as
  `PublisherFieldMetadataService`'s reflection-based one — more important here, since OpenAPI
  schemas can genuinely self-reference).
- `RestOperationDefinition`/`RestApiDefinition` — one per discovered operation/per interface,
  built by `RestApiDefinitionBuilder` walking the parsed `OpenAPI` model (JSON request/response
  media types only; other content types are skipped with a startup warning). Auto-discovered —
  there's no `messages:` list to hand-declare, unlike UDP/TCP.
- `RestSchemaWiringConfig` — the REST analogue of `MessageSchemaWiringConfig.interfaceMessageDefinitionRegistries`:
  a `@Bean Map<String, RestApiDefinition> restApiDefinitions`, one entry per REST interface. Same
  `@Qualifier("restApiDefinitions")` requirement as that other map bean (see the `Map<String, X>`
  gotcha below).
- `RestFieldMetadataService`/`RestRequestBodyAssembler` — the REST analogues of
  `PublisherFieldMetadataService`/`ReflectiveFieldApplier`, producing/consuming the exact same
  `PublisherFieldDto` shape, so the Generic Publisher UI's field-rendering JS
  (`buildGenericFieldRow`/`buildGenericArrayGroup`/`renderFieldsInto`) needs no changes to also
  render REST operation forms. The dotted/indexed flattened-path parsing
  (`unflatten`/`trackData[0].id`-style keys) was extracted out of `ReflectiveFieldApplier` into
  `com.example.schemacore.reflect.FlattenedFieldPathUtil` specifically so both sides could share
  it without a `com.example.monitor` → `com.example.schemacore` dependency going the wrong
  direction.
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
  (`storeAndArchive`, extracted out of `finishIngest` for this purpose) — it deliberately does
  **not** call `dispatchIfEligible`, since there's no `MessageArrivedHandler` to dispatch to for a
  dynamically-discovered operation.
- `RestAutoReplySettingsService` — REST server mode's "auto-reply" is a **mandatory** synchronous
  HTTP response (every request gets *some* response, by necessity of the protocol), not an
  optional async dispatch like `AutoReplySettingsService`, so it's a deliberately independent,
  equally in-memory-only settings store keyed by `(interfaceKey, operationId)`. Falls back to the
  OpenAPI spec's own response schema when nothing's configured: its `example` if present, else a
  synthesized placeholder instance (`""`/`0`/`false`/`[]`/recursive `{}` per leaf type).
- `RestOperationInvoker` — REST client-mode/on-demand publishing, using the JDK's built-in
  `java.net.http.HttpClient` (no new dependency). Wired into `PublisherService.send()` as a REST
  branch (`sendRest`) — unlike UDP/TCP's fire-and-forget send, the whole point is the response:
  it's captured via `MessageIngestionPipeline.ingestRestOperation` as a newly-observed message
  (`messageType` suffixed `" (response)"`), reusing the existing Generic Publisher send flow
  rather than a new independent poller. **Periodic** REST publish is a known gap (see below) —
  `PeriodicPublisherService` is hard-wired to the legacy flat/opcode `PublishRequest` +
  `MonitorPayloadFactory`, which has no way to represent a REST operation at all.
- UI: a separate "REST Publisher" card (not merged into the Generic Publisher's interface/message
  dropdowns, since `PublisherMessageDto` is `Class<?>`+opcode-shaped and doesn't fit an operation)
  plus a "REST Auto-Reply" config panel, both in `index.html`, backed by
  `RestOperationsController` (`/api/rest/interfaces`, `/api/rest/fields`) and
  `RestAutoReplyController` (`/api/rest/{key}/autoreply[/{operationId}]`).

`http://` only in v1 (no HTTPS config surface); `oneOf`/`anyOf` schemas collapse to their first
alternative for form-rendering (`RestSchemaConverter.firstAlternative`) rather than fully modeling
polymorphic bodies.

## Config files

- `config/traffic-tool.yml` — the interfaces/messages/auto-reply config, loaded by
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
ingestion actually decodes against) handle this fine — each interface gets its own isolated
registry. The flat, cross-interface `messageDefinitionRegistry` bean (backs
`MonitorPayloadFactory`'s "encode by opcode"/"encode by message class" API, used by
`/api/publish/udp` and periodic publish) can't: its opcode/class-keyed maps require global
uniqueness, by design (`MessageDefinitionRegistryTest` deliberately asserts duplicates throw —
this catches real config typos, like copy-pasting an interface block and forgetting to bump an
opcode). Rather than relaxing that invariant, `MessageSchemaWiringConfig.messageDefinitionRegistry`
silently excludes a later interface's definition from this *flat view only* when its opcode or
message class was already claimed by an earlier interface — `rada` (declared first) wins, so
`/api/publish/udp` and periodic-publish can't target `rada-le`'s `RadaExtendedStatus` by
interfaceName+messageType either (that lookup is also flat-registry-backed). The scoped-registry
"Generic Publisher" UI (`PublisherService`/`PublisherMetadataService`) is unaffected and works
for both interfaces, since it never touches the flat registry.

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
  their defaults, so it completes before `package`/repackage ever runs. This doesn't surface for
  traffic-monitor-app-core's own REST ITs (see "IT suite lives in traffic-monitor-app... (except
  REST)" above) because that module has no spring-boot-maven-plugin at all, so its failsafe
  execution can safely use the default phases unmodified.
- **`cond ? Long.parseLong(...) : Integer.parseInt(...)` silently returns `Long` always** — Java's
  conditional-expression numeric promotion widens the `int` branch to `long` (binary numeric
  promotion of the two operand types) regardless of which branch actually executes at runtime, so
  a ternary mixing primitive `long`/`int` results autoboxes to `Long` unconditionally. Bit
  `RestRequestBodyAssembler.coerceScalar` coercing an OpenAPI `integer`+`format: int32` field —
  every "integer" field came out as `Long`, not just the `int64` ones. Fix: explicit boxed
  `if`/`yield` branches in the switch expression, not a ternary mixing primitive numeric types.
  Caught by an actual assertion failure (`expected: 3, but was: 3L`), not by inspection.

## Known gaps / natural follow-ups

- `RadaTracksExtended` Instancio generation (see above).
- **Periodic REST publish** isn't wired up — `PeriodicPublisherService` is hard-wired to the
  legacy flat/opcode `PublishRequest` + `MonitorPayloadFactory`, which has no way to represent a
  REST operation at all. On-demand REST publish (`PublisherService.send`/the REST Publisher UI)
  works fully; a periodic variant would need its own scheduler bound to the scoped/generic send
  path (`PublisherSendRequest`), which would also generically benefit UDP/TCP's Generic Publisher
  (today only the legacy Sample Publisher has periodic send at all).
- **REST auto-reply bodies are fully static** — no variable interpolation/templating (e.g. can't
  echo a path parameter back into the configured response). A templated version is a materially
  bigger feature than what's built.
- **No HTTPS for REST** — `RestOperationInvoker` only builds `http://` URIs; there's no TLS config
  surface for REST client-mode targets.
- **`oneOf`/`anyOf` OpenAPI schemas collapse to their first alternative** (`RestSchemaConverter.firstAlternative`)
  rather than fully modeling a polymorphic request/response body in the Generic Publisher UI.
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
