# Traffic Interface Tool

A Java 21 / Maven multi-module toolkit for simulating and observing custom binary protocol
traffic over **UDP, TCP, and REST**. Two runnable apps talk to each other:

- **traffic-monitor-app** — a Spring Boot service that listens for traffic on a set of
  configurable interfaces, decodes it, stores recent messages in memory (plus a durable
  H2-backed history with search/analytics endpoints), exposes a REST API, and serves a
  dark-themed live-monitoring web UI. It can also *publish* messages (once or on a repeating
  schedule), auto-reply to specific inbound message types via pluggable handlers, and exposes
  Micrometer/Actuator metrics (including a Prometheus scrape endpoint). Beyond the binary
  UDP/TCP protocols, it can also host or call REST APIs described entirely by an OpenAPI/Swagger
  YAML file — no Java code required per API (see [REST interfaces](#rest-interfaces-dynamic-no-codegen)).
- **traffic-tester-app** — a standalone CLI app that sends synthetic traffic (defined in a YAML
  scenario file) at the monitor, and can optionally listen for messages sent back.

## Module layout (3 modules)

```
traffic-monitor-app-core   The generic engine: ingestion, persistence, analytics, auto-reply,
                            publisher, interface runtime control, REST API, UI resources.
                              - com.example.schemacore (+ .annotation/.envelope/.reflect) —
                                MessageDefinition/Registry, ProtocolMessage marker, the
                                reflective codec engine.
                              - com.example.handlercore — MessageArrivedHandler<T>,
                                MessageHandlerRegistry, MessageArrivedDispatcher, ReplySender,
                                DestinationConfig.
                              - com.example.monitor — the engine itself, including .rest
                                (+ .ingestion.rest) — dynamic, no-codegen OpenAPI/Swagger-driven
                                REST interface support (see REST interfaces below).
                            Has zero compile dependency on any concrete protocol/handler
                            classes — enforced by its pom.xml, not just convention. (REST
                            interfaces take this further still — they need no Java class at
                            all, concrete or otherwise.)

traffic-monitor-app        The runnable app. Package layout adds two more top-level packages
                            alongside com.example.monitor:
                              - com.example.schemas — concrete message classes
                                (fruit/weather/ping/candy/rada).
                              - com.example.messagehandlers — concrete MessageArrivedHandler
                                implementations, one per message type.
                            Holds TrafficMonitorApplication's main(), the
                            spring-boot-maven-plugin config, and this module's integration-test
                            suite (real sockets, real Spring context).

traffic-tester-app         Standalone CLI tester (no Spring), depends on traffic-monitor-app for
                            the message classes — it's a test tool, allowed to know the wire
                            format.
```

Build/test a module + its deps: `mvn -pl <module> -am test`. Full repo: `mvn clean verify` from
the root.

## Core architectural invariant: the engine has zero schema dependency

`traffic-monitor-app-core` never imports `com.example.schemas.*` or
`com.example.messagehandlers.*` in main code. All wiring from the generic engine to concrete
protocol classes happens by **fully-qualified class name string**, read from YAML
(`config/traffic-tool.yml`) and resolved via `Class.forName` at startup. **This is what makes
"adding a new interface" a config + new-classes exercise, not a change to the engine** — see
[Adding a new interface](#adding-a-new-interface) below.

## Interfaces currently configured

Every interface owns its **own dedicated socket** — its own port and its own protocol (`UDP` or
`TCP`), individually startable/stoppable/reconfigurable at runtime (see
[Interface runtime control](#interface-runtime-control)). There is no shared/legacy port model.

| Interface | Port | Protocol | Messages | Header |
|---|---|---|---|---|
| Fruit Interface | 5001 | UDP | Orange, Banana | default envelope |
| Ping Interface | 5002 | UDP | Ping, Pong | default envelope |
| Weather Interface | 5003 | UDP | TemperatureReading | default envelope |
| Candy Interface | 5004 | TCP | Candy | default envelope |
| Rada Interface | 5050 | UDP | RadaStatus, RadaExtendedStatus, RadaExtendedStatusMrs, RadaTracksExtended | custom `RadaHeader`, self-parsing |
| Rada Interface (Little Endian Extended Status) | 5051 | UDP | RadaExtendedStatus (same class as Rada above, decoded `byteOrder: LITTLE_ENDIAN` instead) | custom `RadaHeader`, self-parsing |
| Pets REST Interface | 5060 | REST | `getPet`, `createPet` — auto-discovered from `swagger/pets-demo.yml`, not hand-declared | n/a — JSON over HTTP, described by the OpenAPI schema, not a binary header |

Pets is a **REST** interface, not UDP/TCP — see [REST interfaces](#rest-interfaces-dynamic-no-codegen)
below; the rest of this section (header models, `messageClass:`, opcodes) is UDP/TCP-specific.

"Default envelope" vs "custom header, self-parsing" is explained in
[The reflective codec convention](#the-reflective-codec-convention) below — it's the single most
important thing to understand before adding a new interface.

## The reflective codec convention

Messages don't need a hand-written `MessageDefinition` + separate codec class pair.
`ReflectiveStructCodec` (`com.example.schemacore.reflect`) reflectively invokes methods that
follow a naming convention on the message class itself:

- **Decode** (first match wins): `public static T fromByteBuffer(ByteBuffer)` (records — immutable,
  can't self-mutate) — else `public T(byte[])` constructor (mutable classes that parse themselves
  in the constructor, e.g. the Rada messages).
- **Encode** (first match wins): `public byte[] toByteArray()` — no-arg, self-sizing, used when a
  field is variable-length (e.g. a `String` — `StructSizeCalculator` can't size those) — else
  `public void toByteArray(ByteBuffer)`, buffer pre-sized via
  `StructSizeCalculator.calculateStructSize(class)` (fixed-layout messages only; array fields
  additionally need `@FixedArrayLength(n)` from `com.example.schemacore.annotation`).

`ReflectiveMessageDefinition(interfaceName, messageType, opcode, messageClass)` wraps this into a
`MessageDefinition` — one line of config (`messageClass:` + `opcode:` in
`config/traffic-tool.yml`) replaces a hand-written Java class entirely. There's also a legacy
`definitionClass:` config form for a fully hand-written `MessageDefinition`, but every message in
this repo today uses the reflective style — **use it for new messages too, unless you have a
specific reason not to.**

`ReflectiveFieldExtractor`/`ReflectiveFieldApplier` convert message objects ↔ generic
`Map<String,Object>` (used for the UI/history JSON and the generic publisher). Enums with a
`getWireName()` method are represented by that value both ways (case-insensitive on input);
everything else falls back to the Java constant name. String field values from HTTP/JSON/HTML
form input are coerced to the target's real numeric/boolean type on the way in.

## Two header models: default envelope vs. custom/self-parsing

Every interface picks **one** of two header models via `InterfaceConfig`:

**Default envelope (`messageOwnsHeader: false`, the default — use this unless you have a
specific reason not to):** the engine handles the header for you. Every message on the interface
is preceded by the same fixed 16-byte header (`com.example.schemacore.envelope.DefaultEnvelopeHeader`):

| Field | Type | Bytes | Notes |
|---|---|---|---|
| `opcode` | int32 | 4 | identifies the message type |
| `sendTimeEpochMillis` | int64 | 8 | sender's timestamp |
| `bodyLength` | int32 | 4 | length of the body that follows (also used to frame messages on a TCP stream) |

Your message class only ever encodes/decodes its **body** — the pipeline strips the header
before calling your `fromByteBuffer`/constructor, and the publisher/tester prepend
`ProtocolHeaderCodec.encodeMessage(opcode, timestamp, body)` around your `toByteArray()` output.
This is what Fruit/Weather/Ping/Candy all use.

**Custom/self-parsing header (`messageOwnsHeader: true`):** your message class parses *and*
emits its own header as part of its own `fromByteBuffer`/`toByteArray` — the full payload
(header + body) is passed through unchanged on both decode and encode, with no engine-side
wrap/strip at all. Use this when you're interoperating with an existing wire format that has its
own header shape (opcode field name/position/type, extra fields, different byte order, etc.) —
Rada is the worked example (`RadaHeader`, `opcodeFieldName: msgType`). You'll also need
`headerType:` pointing at your header class, and if the interface runs over **TCP**, your header
needs an integer field the engine can read as the body length for stream framing
(`bodyLengthFieldName:`, see below) — `RadaHeader` doesn't have one because Rada is UDP-only, so
TCP framing never comes up for it.

## Adding a new interface

This is the main workflow this README exists to document. A new interface needs, at minimum, a
config entry and at least one message class — nothing in `traffic-monitor-app-core` is ever
touched. Everything below assumes the common case (`messageOwnsHeader: false`, the default
envelope) unless a step says otherwise; see the callouts for the custom-header path.

### Step 1 — Pick a key, a name, a port, and a protocol

- `key`: short, lowercase, unique across `config/traffic-tool.yml` — used in URLs
  (`/api/interfaces/{key}/start`) and as the map key in a few places. Pick something stable; it's
  not meant to change.
- `name`: the human-readable display name — shown in the UI, used as `interfaceName` in
  `ObservedMessage`/handlers/auto-reply settings, and must be unique too.
- `port`: **required** — `TrafficToolConfigLoader` fails Spring context startup immediately if
  any interface is missing one. Pick something free; check against every other interface's
  `port:` in the same file (5001/5002/5003/5004/5050 are already taken — see
  [Interfaces currently configured](#interfaces-currently-configured)). This is only the
  **default**; it can be changed later at runtime without a restart (see
  [Interface runtime control](#interface-runtime-control)) or in config before the next restart.
- `protocol`: `UDP` or `TCP`. Pick UDP unless you specifically want a persistent-connection,
  ordered-delivery transport for this interface. Both are equally supported — `UdpIngestionRunner`
  and `TcpIngestionRunner` both implement the exact same per-interface dedicated-socket mechanism
  (open on startup if `enabled: true`, or later via the REST API), decoding through the same
  `MessageIngestionPipeline`.

### Step 2 — Write the message class(es)

Message classes live in `traffic-monitor-app/src/main/java/com/example/schemas/<protocol>/` and
implement `ProtocolMessage` (from `com.example.schemacore`, an empty marker interface — no other
methods required beyond what `ReflectiveStructCodec` looks for). Pick the shape based on your
field types:

**Variable-length field (a `String`, most commonly) → a record with a self-sizing no-arg
`toByteArray()`.** This is the common case — what `OrangeMessage`/`CandyMessage` already do:

```java
package com.example.schemas.beacon;

import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record BeaconPingMessage(String label, int strength) implements ProtocolMessage {
    public static BeaconPingMessage fromByteBuffer(ByteBuffer buffer) {
        int labelLength = buffer.getInt();
        byte[] labelBytes = new byte[labelLength];
        buffer.get(labelBytes);
        int strength = buffer.getInt();
        return new BeaconPingMessage(new String(labelBytes, StandardCharsets.UTF_8), strength);
    }

    public byte[] toByteArray() {
        byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + labelBytes.length + Integer.BYTES);
        buffer.putInt(labelBytes.length);
        buffer.put(labelBytes);
        buffer.putInt(strength);
        return buffer.array();
    }
}
```

**Fixed-size fields only (no `String`) → a `toByteArray(ByteBuffer)` instead**, with the buffer
pre-sized via `StructSizeCalculator.calculateStructSize(class)`; array fields additionally need
`@FixedArrayLength(n)` from `com.example.schemacore.annotation` (see
`com.example.schemas.rada.messages.RadaTracksExtended` for a worked array-heavy example, and
`com.example.schemas.rada.messages.RadaExtendedStatus` for a simpler scalar-only one). Fixed-layout
messages can be either a record (`fromByteBuffer`) or, if the class needs to parse itself in
place rather than via a static factory, a mutable class with a `public T(byte[])` constructor —
see `RadaStatus` for that variant.

`ReflectiveStructCodec` finds these methods by name and signature alone — there's no interface
to implement beyond `ProtocolMessage`, and no hand-written codec or `MessageDefinition` class to
write, regardless of which shape you pick.

**If this interface uses a custom/self-parsing header** (`messageOwnsHeader: true`), your message
class's `fromByteBuffer`/constructor and `toByteArray()`/`toByteArray(ByteBuffer)` must read/write
the header themselves as the first thing they do (see `RadaStatus.fromByteArray`, which calls
`header.fromByteArray(buffer)` before anything else) — you'll also write a small header struct
class (see `com.example.schemas.rada.struct.RadaHeader`) using the exact same
`fromByteBuffer`/`toByteArray(ByteBuffer)` convention.

### Step 3 — Register it in `config/traffic-tool.yml`

Add a new top-level entry under `interfaces:`:

```yaml
  - key: beacon
    name: Beacon Interface
    protocol: UDP
    port: 5005
    messages:
      - type: BeaconPing
        messageClass: com.example.schemas.beacon.BeaconPingMessage
        opcode: 6001
    autoReply:
      enabled: false
      host: localhost
      port: 7001
```

Field reference (`InterfaceConfig`/`MessageConfig`):

| Field | Required? | Default | Notes |
|---|---|---|---|
| `key` | yes | — | unique, used in URLs |
| `name` | yes | — | unique, shown in UI, `interfaceName` everywhere |
| `protocol` | no | `UDP` | `UDP` or `TCP` |
| `port` | **yes** | — | fails startup if missing |
| `enabled` | no | `true` | if `false`, doesn't auto-start at boot (still startable via REST/UI) |
| `headerType` | no | `com.example.schemacore.envelope.DefaultEnvelopeHeader` | only set this for a custom header |
| `opcodeFieldName` | no | `opcode` | the header field name holding the opcode; only change for a custom header whose opcode field is named differently |
| `messageOwnsHeader` | no | `false` | `true` for a custom/self-parsing header (see [Two header models](#two-header-models-default-envelope-vs-customself-parsing)) |
| `bodyLengthFieldName` | no | `bodyLength` | only relevant for **TCP** interfaces with a **custom** header — the header field the engine reads to know how many body bytes follow, for stream framing |
| `byteOrder` | no | `BIG_ENDIAN` | currently informational only |
| `messages[].type` | yes | — | unique per interface, shown in UI, matched by handlers |
| `messages[].messageClass` | yes* | — | fully-qualified class name, reflective style (recommended) |
| `messages[].definitionClass` | yes* | — | fully-qualified hand-written `MessageDefinition` class (legacy style — mutually exclusive with `messageClass`) |
| `messages[].opcode` | yes, if using `messageClass` | — | must be unique **within this interface's own messages** (each interface has its own scoped opcode table — opcodes don't need to be globally unique across interfaces) |
| `autoReply.enabled` | no | `false` | see [Auto-reply message handlers](#auto-reply-message-handlers) |
| `autoReply.host`/`port`/`transport` | no | `localhost`/`7001`/`UDP` | default auto-reply destination |
| `shouldBroadcast`/`broadcastTargets` | no | `false`/`[]` | fan a publish out to a fixed list of `host:port` targets — see `PublisherService` |

`TrafficToolConfigLoader` validates all of this at startup and fails fast (a clear exception, not
a silent misconfiguration) on: a missing interface `port`, zero messages on an interface, a
message with neither `messageClass` nor `definitionClass`, or a `messageClass` entry missing its
`opcode`.

### Step 4 — (Optional) React to it with a handler

Every successfully decoded message is dispatched (asynchronously, off the ingestion thread) to a
per-message-type `onMessageArrived` hook. Handler classes live in
`traffic-monitor-app/src/main/java/com/example/messagehandlers/<protocol>/`, are Spring
`@Component`s (picked up automatically — `TrafficMonitorApplication`'s `scanBasePackages`
includes `com.example.messagehandlers`), and implement `MessageArrivedHandler<T>` where `T` is
your new message class — it arrives **already decoded into its real type**, no `Map` unpacking or
casting:

```java
package com.example.messagehandlers.beacon;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.beacon.BeaconPingMessage;
import org.springframework.stereotype.Component;

@Component
public class BeaconPingMessageHandler implements MessageArrivedHandler<BeaconPingMessage> {
    @Override
    public String interfaceName() {
        return "Beacon Interface"; // must match config/traffic-tool.yml's `name:`
    }

    @Override
    public String messageType() {
        return "BeaconPing"; // must match config/traffic-tool.yml's `type:`
    }

    @Override
    public void onMessageArrived(BeaconPingMessage message, ReplySender replySender, DestinationConfig destinationConfig) {
        // no-op is fine if you don't need to react — see BananaMessageHandler for the pattern.
        // To reply: if (destinationConfig != null) {
        //     replySender.reply(message, destinationConfig.host(), destinationConfig.port(), destinationConfig.transport());
        // }
    }
}
```

If you skip this step, the message still decodes, gets stored, and shows up in the Live/History
UI — it just won't trigger anything on arrival. `MessageHandlerRegistry` fails Spring context
startup fast on a duplicate `interfaceName`+`messageType` registration, same fail-fast philosophy
as the config loader.

### Step 5 — (Optional) Let the tester app send it

`traffic-tester-app` doesn't discover message types dynamically — each sendable message needs an
explicit `PayloadMode` enum entry and a `PayloadFactory` case:

1. Add a constant to `traffic-tester-app/.../config/PayloadMode.java`, e.g. `BEACON_PING`.
2. Add a case + private builder method in `traffic-tester-app/.../payload/PayloadFactory.java`:

   ```java
   case BEACON_PING -> createBeaconPing(config);
   ```

   ```java
   private byte[] createBeaconPing(PayloadConfig config) {
       BeaconPingMessage message = new BeaconPingMessage(config.getBeacon().getLabel(), config.getBeacon().getStrength());
       return encodeMessage(BEACON_PING_OPCODE, message);
   }
   ```

   For a `messageOwnsHeader: true` interface, don't call the shared `encodeMessage(opcode, message)`
   helper (that wraps in the default envelope) — instead call `ReflectiveStructCodec.encode(message)`
   directly, since the message class already emits its own header. See `createRadaStatus` in the
   same file for that pattern, and `createRadaTracksExtended` for the extra care needed with
   `@FixedArrayLength` array fields (Instancio doesn't understand that annotation — see the
   comment on that method for the workaround).
3. If the message needs caller-supplied fields (like `label`/`strength` above), add a
   `BeaconPayloadConfig` class next to `CandyPayloadConfig`/`PingPayloadConfig` and a getter on
   `PayloadConfig`. If it's fully random/self-contained (like the Rada messages, generated via
   `Instancio.create(...)`), skip this — no config class needed.
4. Add an entry to `config/tester-scenario.yml` so a normal tester run exercises it:

   ```yaml
     - mode: BEACON_PING
       target:
         host: 127.0.0.1
         port: 5005
       beacon:
         label: "beacon-01"
         strength: 7
   ```

### Step 6 — Add to the test config and write tests

Make the same `config/traffic-tool.yml` edit to
`traffic-monitor-app/src/test/resources/traffic-tool-test.yml` (used by the integration-test
suite) — pick a port in the `25000` range there to match the existing convention
(25001/25002/25003/25004/25050 are taken; `AbstractIntegrationTestBase` also dynamically
reassigns fresh free ports per test-context anyway, substituting whatever literal `port: NNNNN`
values appear in that file, so keep using plain integers there, not expressions).

At minimum, write:

- A round-trip encode/decode unit test on the message class itself (see
  `CandyMessageTest.toByteArray_thenFromByteBuffer_roundTripsNameAndCalories` for the pattern).
- If you added a handler, a test asserting `interfaceName()`/`messageType()` and whatever
  `onMessageArrived` does (see `CandyMessageHandlerTest`).
- If you want end-to-end coverage (real socket, real Spring context), an `*IT.java` test in
  `traffic-monitor-app/src/test/java/com/example/monitor/` extending
  `AbstractIntegrationTestBase` — see `UdpIngestionEndToEndIT`/`TcpIngestionEndToEndIT` for the
  pattern (`sendUdp(port, payloadBytes)` / `sendTcp(port, payloadBytes)`, then
  `awaitStoreContains(predicate)`).

### Step 7 — Build and verify

```bash
mvn -pl traffic-monitor-app -am test      # unit tests
mvn -pl traffic-monitor-app -am verify    # also runs the integration suite (real sockets, real
                                           # Spring context) — catches config typos and
                                           # duplicate-opcode mistakes before you'd hit them at
                                           # runtime
```

Then run it for real and confirm the new interface opens its socket:

```bash
mvn -pl traffic-monitor-app-core,traffic-monitor-app -am -DskipTests package
java -jar traffic-monitor-app/target/traffic-monitor-app-1.0-SNAPSHOT-exec.jar
```

Look for a log line like `UDP ingestion started on port 5005 for interface Beacon Interface`,
then `curl localhost:8080/api/interfaces` to confirm it's listed and `"listening": true`.

### Adding just a new message to an *existing* interface

Simpler — skip Steps 1 and 3's interface-level fields entirely. Write the message class (Step 2),
append a new entry under that interface's existing `messages:` list in
`config/traffic-tool.yml` with a fresh `opcode` (unique within that interface), optionally add a
handler (Step 4) and tester support (Step 5), and test (Step 6). No new port, no new top-level
config entry.

## REST interfaces (dynamic, no codegen)

Everything above (message classes, `messageClass:`/`opcode:`, handlers) is for the two **binary**
protocols. `protocol: REST` interfaces are a completely different, much simpler path: describe
the API with an OpenAPI/Swagger YAML file, point a config entry at it, and restart — **no Java
code at all**, not even a message class. This is deliberate: dropping in a new REST API is a
restart, not a rebuild, unlike UDP/TCP where a message still needs a hand-written/reflective class.

A REST interface can run in either direction, same as TCP:

- **`mode: SERVER`** (default) — the monitor **hosts** the API: it binds its own dedicated
  embedded HTTP server on `port`, and every inbound request that matches one of the swagger
  file's operations is decoded, stored, and shown in Live/History exactly like an inbound
  UDP/TCP message — with `messageType` set to the OpenAPI `operationId` (e.g. `getPet`) instead
  of a `messages[].type:`. Every request also gets a response written back synchronously (see
  [REST auto-reply](#rest-auto-reply) below) — HTTP requires *some* response, so there's no
  "auto-reply disabled" state the way there is for UDP/TCP.
- **`mode: CLIENT`** — the monitor **calls out** to an external REST API at `host:port` instead.
  There's no persistent connection to maintain (unlike TCP client mode) — client mode is purely
  on-demand, driven by the same Generic Publisher UI/API used for UDP/TCP (see
  [REST Publisher](#rest-publisher) below). The HTTP response comes back and is itself captured
  as a newly-observed message (`messageType` suffixed `" (response)"`), so calling an external API
  and inspecting what it returned both happen in the same place as everything else in this tool.

Because REST messages have no backing Java class, they're handled by an entirely separate code
path (`com.example.monitor.rest` + `com.example.monitor.ingestion.rest`) keyed by `operationId`
instead of opcode/message class — they don't go through `ReflectiveStructCodec`,
`MessageDefinitionRegistry`, or `MessageArrivedHandler`. One consequence: **you can't write a
custom handler that reacts to a REST message** the way `OrangeMessageHandler`/`PingMessageHandler`
do for UDP/TCP — see [Known gaps](#known-gaps).

### Adding a new REST interface

1. **Get (or write) an OpenAPI/Swagger YAML file** describing the API and drop it under
   `swagger/` at the repo root (e.g. `swagger/orders-api.yml`). Standard OpenAPI 3.x — paths,
   operations (`operationId`, path/query parameters, JSON request/response bodies), and
   `components.schemas` are all supported; `$ref`/`allOf`/`oneOf`/`anyOf` are resolved
   automatically at startup. JSON media types only — a non-JSON `content:` entry is skipped with
   a startup warning, not supported. `oneOf`/`anyOf` collapse to their first listed alternative
   for form-rendering purposes (full polymorphic body support is a known gap).
2. **Add an interface entry** to `config/traffic-tool.yml`:

   ```yaml
     - key: orders
       name: Orders REST Interface
       protocol: REST
       port: 5061                 # SERVER mode: the local bind port. CLIENT mode: the port to call.
       swaggerFile: swagger/orders-api.yml
       # mode: SERVER (default) — omit for server mode.
       # For CLIENT mode instead:
       # mode: CLIENT
       # host: orders.example.internal
   ```

   No `messages:` list — operations (`operationId`s) are auto-discovered from `swaggerFile` at
   startup, not hand-declared. `TrafficToolConfigLoader` fails fast at startup if `swaggerFile` is
   missing, blank, or doesn't exist on disk.
3. **That's it for server mode.** Build and run, then confirm the operations were discovered:

   ```bash
   curl localhost:8080/api/rest/interfaces
   ```

   should list `orders` with every operation the swagger file defines. Hit one directly:

   ```bash
   curl -X POST localhost:5061/orders -H 'Content-Type: application/json' -d '{"item":"widget"}'
   ```

   and confirm it shows up in `curl localhost:8080/api/messages/recent`.
4. **(Optional) Configure the auto-reply** for server mode — see
   [REST auto-reply](#rest-auto-reply) below; if you skip this, requests still get a response,
   just a placeholder synthesized from the OpenAPI response schema rather than one you chose.
5. **For client mode**, trigger a call via the **REST Publisher** card in the web UI's Sample
   Publisher tab (or `POST /api/publisher/send` directly — see [REST Publisher](#rest-publisher)),
   and confirm the response lands in Live/History as `<operationId> (response)`.
6. **Add to the test config and write tests** — same idea as
   [Step 6](#step-6--add-to-the-test-config-and-write-tests) for UDP/TCP, except REST's own
   integration tests live in `traffic-monitor-app-core` (not `traffic-monitor-app`), specifically
   because REST needs no concrete schema/handler class at all — see `RestServerIngestionIT`/
   `RestClientPublishingIT` and `src/test/resources/rest/sample-openapi.yml` for the pattern
   (a `@DynamicPropertySource`-generated temp config with a fresh free port, same technique
   `AbstractIntegrationTestBase` uses for UDP/TCP).

### REST auto-reply

REST server mode's "auto-reply" is the synchronous HTTP response written back for every request —
mandatory, not an optional switch like UDP/TCP's. Configure one via the **REST Auto-Reply** card
on the Auto-Reply tab (interface + operation + status code + JSON response body), or:

```bash
curl -X POST localhost:8080/api/rest/orders/autoreply/getOrder \
  -H 'Content-Type: application/json' \
  -d '{"statusCode": 200, "bodyTemplate": "{\"status\":\"shipped\"}"}'
```

If nothing's configured for an operation, the response falls back to the OpenAPI spec's own
response schema — its `example` if present, else a synthesized placeholder instance (`""` for
strings, `0` for numbers, `false` for booleans, recursively for nested objects/arrays). Response
bodies are **fully static** — no variable interpolation (e.g. echoing a path parameter back into
the reply) — see [Known gaps](#known-gaps).

### REST Publisher

The Sample Publisher tab's **REST Publisher** card lists every REST interface + operation
(`/api/rest/interfaces`), builds a form from the operation's path/query parameters and request
body (`/api/rest/fields`, using the exact same field-rendering UI as the Generic Publisher,
including boxed nested objects and add/remove-able array-of-struct rows), and sends via the same
`POST /api/publisher/send` endpoint UDP/TCP's Generic Publisher already uses — `messageType` is
the `operationId`. The external API's HTTP response is captured as a newly-observed message,
which is the entire point of REST client mode (unlike UDP/TCP's fire-and-forget publish).
**Periodic** REST publish isn't wired up yet — see [Known gaps](#known-gaps).

## Interface runtime control

Beyond config-time defaults, every interface can be started, stopped, and reconfigured (port
and/or protocol) at runtime — no restart needed, from either the REST API or the **Interfaces**
tab in the web UI (per-row editable port input + protocol dropdown + Save button, alongside
Start/Stop). Runtime changes are **in-memory only** — they reset back to whatever
`config/traffic-tool.yml` says on the next restart.

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET | `/api/interfaces` | — | List every interface with its current key/name/protocol/port/listening state/received & parse-error counts/last-observed timestamp |
| POST | `/api/interfaces/{key}/start` | — | Opens the socket on the interface's current port/protocol |
| POST | `/api/interfaces/{key}/stop` | — | Closes the socket |
| POST | `/api/interfaces/{key}/configure` | `{ "port": 6001, "protocol": "TCP" }` | Changes port and/or protocol — **rejected while the interface is listening**; stop it first |

```bash
curl -X POST localhost:8080/api/interfaces/beacon/stop
curl -X POST -H "Content-Type: application/json" \
  -d '{"port":6005,"protocol":"TCP"}' \
  localhost:8080/api/interfaces/beacon/configure
curl -X POST localhost:8080/api/interfaces/beacon/start
```

Switching an interface's protocol works because both `UdpIngestionRunner` and
`TcpIngestionRunner` implement the identical dedicated-socket-per-interface mechanism against the
same `InterfaceConfig`/`MessageIngestionPipeline` — `InterfaceControlService` just dispatches to
whichever runner matches the interface's *current* protocol. `RestIngestionRunner` is the third
option the protocol dropdown offers, but with one asymmetry worth knowing: **switching an
existing UDP/TCP interface to `REST` at runtime doesn't actually work** —
`InterfaceConfigureRequest` has no `swaggerFile` field, and even if it did, the set of REST
interfaces is parsed from config once at startup, not rebuilt on reconfigure. The dropdown still
needs the `REST` option so a REST interface's *own* row displays its protocol correctly — only
interfaces already declared `protocol: REST` in `traffic-tool.yml` at startup are functional.

## Config files

- **`config/traffic-tool.yml`** (path from env var `TRAFFIC_TOOL_CONFIG`, default
  `config/traffic-tool.yml` relative to the working directory — run from the repo root) — the
  interfaces/messages/auto-reply config described above. Test equivalent:
  `traffic-monitor-app/src/test/resources/traffic-tool-test.yml`.
- **`swagger/`** — OpenAPI/Swagger YAML files for `protocol: REST` interfaces, referenced by
  `swaggerFile:` (see [REST interfaces](#rest-interfaces-dynamic-no-codegen)).
  `swagger/pets-demo.yml` is the demo spec behind the `pets` interface.
- **`traffic-monitor-app-core/src/main/resources/application.yml`** (Spring Boot) — HTTP port,
  UDP receive buffer size, TCP max body length, recent-message store size, the H2 datasource, and
  Actuator endpoint exposure:

  ```yaml
  server:
    port: 8080

  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics,prometheus

  spring:
    datasource:
      url: jdbc:h2:file:${TRAFFIC_MONITOR_DB_PATH:./data/traffic-monitor};AUTO_SERVER=TRUE
    sql:
      init:
        mode: always

  traffic:
    udp:
      buffer-size-bytes: 65507
    tcp:
      max-body-length-bytes: 65507
    store:
      max-size: 500
  ```

  Per-interface ports/protocols/enabled state live in `traffic-tool.yml`, not here — this file
  only holds settings that apply globally across every interface.
- **`config/tester-scenario.yml`** — `traffic-tester-app`'s scenario definition (what to send,
  how often, to which target) — see [Scenario configuration](#scenario-configuration).

## Monitor's own REST API

(Not to be confused with `protocol: REST` *interfaces* — this is the control-plane/data-access
API the monitor app itself exposes, always present regardless of which interfaces you configure.)

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET | `/api/messages/recent` | — | Current in-memory `ObservedMessage` list (newest first) |
| GET | `/api/messages/history` | — (query params) | Paged, filtered search over durable H2 history |
| GET | `/api/analytics/timeseries` | — (query params) | Message counts bucketed by time (`minute`/`hour`/`day`) |
| GET | `/api/analytics/breakdown` | — (query params) | Message counts grouped by `interfaceName` or `messageType` |
| GET / POST / POST | `/api/interfaces*` | see above | [Interface runtime control](#interface-runtime-control) |
| GET | `/api/publisher/interfaces` | — | Every configured UDP/TCP interface + its message types (backs the Generic Publisher UI and the left sidebar) |
| GET | `/api/publisher/fields` | — (query params) | Field metadata (name/type/enum options, incl. nested/array-of-struct) for one message class, via reflection |
| POST | `/api/publisher/send` | `PublisherSendRequest` | Sends one message for any UDP/TCP/REST interface (`interfaceKey`+`messageType`+`fields`) — the endpoint behind both the Generic Publisher and the REST Publisher |
| GET | `/api/rest/interfaces` | — | Every configured `protocol: REST` interface + its operations, auto-discovered from `swaggerFile` |
| GET | `/api/rest/fields` | `interfaceKey`, `operationId` (query params) | Field metadata for one REST operation's request body, walking the OpenAPI schema instead of reflecting a class |
| GET | `/api/rest/{key}/autoreply` | — | Resolved (configured-or-fallback) auto-reply for every operation on one REST interface |
| POST | `/api/rest/{key}/autoreply/{operationId}` | `{ statusCode, bodyTemplate }` | Configures the static response for one REST operation |
| POST | `/api/publish/udp` | `PublishRequest` | Sends one message over UDP or TCP (`transport` in the body selects the transport, default UDP) — the legacy/Sample Publisher endpoint, fruit/weather/ping/candy only |
| POST | `/api/publish/udp/periodic/start` | `PeriodicPublishRequest` | Starts repeating publish (legacy `PublishRequest`-based — UDP/TCP only, no REST support yet) |
| POST | `/api/publish/udp/periodic/stop` | — | Stops the periodic publisher |
| GET | `/api/publish/udp/periodic/status` | — | Current `PeriodicPublishStatus` |
| GET | `/api/autoreply/settings` | — | Global + per-interface UDP/TCP auto-reply settings |
| POST | `/api/autoreply/global` | `{ enabled }` | Sets the global auto-reply switch |
| POST | `/api/autoreply/interface` | `{ interfaceName, enabled, host, port, transport }` | Sets one UDP/TCP interface's switch + destination + reply transport |

`PublishRequest`: `interfaceName`, `messageType`, `host`, `port`, `transport`
(`"UDP"` \| `"TCP"`, optional, defaults to `"UDP"`), `fields` (`Map<String,Object>`).

```json
POST /api/publish/udp
{
  "interfaceName": "Fruit Interface",
  "messageType": "Banana",
  "host": "localhost",
  "port": 7001,
  "fields": { "color": "yellow", "weight": 142.75 }
}
```

## Web UI

Served at `http://localhost:8080`. Left sidebar: **Supported Interfaces and Messages** — every
configured interface as a clickable chip with its message types listed below it. Click an
interface to deactivate all of its messages in the Live Messages table (click again to
reactivate); click a single message to toggle just that one independently.

Five tabs:

- **Live Messages** — table of observed messages, polling `/api/messages/recent` every ~2s, with
  a click-to-inspect JSON detail panel. Filtered by whatever's currently active in the sidebar.
- **Interfaces** — one row per configured interface: editable port input + protocol dropdown +
  Save, plus Start/Stop, listening state, received/parse-error counts, last-observed time — see
  [Interface runtime control](#interface-runtime-control).
- **Sample Publisher** — three cards: the original hand-coded Sample Publisher (Fruit/Weather
  only, host:port:transport + per-field inputs, enum fields as dropdowns, "Send Once" plus
  periodic controls); the **Generic Publisher**, which works for any UDP/TCP interface via
  reflection (no hardcoded per-message forms, including nested/array-of-struct field rendering);
  and the **REST Publisher** (see [REST Publisher](#rest-publisher)), which reuses the Generic
  Publisher's field-rendering for any `protocol: REST` interface's operations.
- **Auto-Reply** — master toggle + one row per UDP/TCP interface (host/port/transport), built
  entirely from the API response so new interfaces show up automatically, plus a separate
  **REST Auto-Reply** card (see [REST auto-reply](#rest-auto-reply)) since REST's response model
  is different enough (mandatory, per-operation, status+body) not to fit the same UI.
- **History** — search/filter the durable H2-backed history plus time-series/breakdown analytics
  charts.

## Auto-reply message handlers

See [Step 4](#step-4--optional-react-to-it-with-a-handler) above for the mechanics of writing a
handler. Two independent gates control whether it actually fires and where the reply goes, both
defaulting from `config/traffic-tool.yml` and both live-editable via the UI/API without a
restart:

- **Global switch** — a single master on/off for the whole mechanism.
- **Per-interface switch + destination** — each interface has its own `enabled` flag plus a
  `host`/`port`/`transport`.

`MessageIngestionPipeline` checks both switches right after the parse-error check; if either is
off, the handler never runs. If it passes, it resolves the interface's destination into a
`DestinationConfig` (`null` if unconfigured) and passes it as `onMessageArrived`'s third argument
— the handler explicitly uses `destinationConfig.host()/port()/transport()` when calling
`replySender.reply(...)`. The reply transport is independent of the transport the triggering
message arrived on. `ReplySender` resolves which `MessageDefinition` to encode with via
`MessageDefinitionRegistry.findByMessageClass(message.getClass())` — no string-based dispatch.

Worked example — `OrangeMessageHandler` replies with a Banana when an Orange arrives with
`freshness == not_fresh`; `PingMessageHandler` always replies with a Pong echoing the same
sequence. `BananaMessageHandler`/`TemperatureReadingMessageHandler`/`CandyMessageHandler` are
no-op stubs demonstrating the "decode and store, but don't react" case.

## Persistence and history

Every observed message (successfully decoded or not, any transport) is written to two places:
`RecentMessageStore` (in-memory bounded ring buffer, backs `/api/messages/recent`, lost on
restart) and `MessageArchiveRepository` (durable H2-backed, backs `/api/messages/history` and
`/api/analytics/*`, survives restarts). Archiving happens asynchronously so a slow/failed write
never blocks ingestion — failures are logged and increment `network_monitor.archive.failures`
rather than propagating.

Storage is an embedded H2 database, file-backed by default
(`jdbc:h2:file:${TRAFFIC_MONITOR_DB_PATH:./data/traffic-monitor};AUTO_SERVER=TRUE`), schema in
`traffic-monitor-app-core/src/main/resources/schema.sql` (one `messages` table, applied
idempotently via `spring.sql.init.mode: always` on every startup).

## Metrics and observability

`spring-boot-starter-actuator` + `micrometer-registry-prometheus` expose `health`, `info`,
`metrics`, `prometheus` under `/actuator/*`. Application metrics, all prefixed
`network_monitor.*`:

| Metric | Type | Tags | Where |
|---|---|---|---|
| `network_monitor.messages.received` | Counter | `transport`, `interfaceName`, `parseError` | `MessageIngestionPipeline` — once per inbound message |
| `network_monitor.messages.payload_size_bytes` | DistributionSummary | `transport` | `MessageIngestionPipeline` |
| `network_monitor.archive.failures` | Counter | `transport` | H2 archive write failed |
| `network_monitor.dispatch.failures` | Counter | `interfaceName` | an `onMessageArrived` handler threw |
| `network_monitor.tcp.connections.accepted` | Counter | `port` | once per accepted TCP connection |
| `network_monitor.tcp.connections.active` | Gauge | — | current open TCP connection count, across all TCP interfaces |
| `network_monitor.tcp.connections.errors` | Counter | `port` | genuine connection-handling errors |
| `network_monitor.udp.listener.errors` | Counter | `port` | genuine socket errors while listening |
| `network_monitor.messages.sent` | Counter | `transport` | successful outbound send (publish or auto-reply) |
| `network_monitor.messages.send_errors` | Counter | `transport` | outbound send failed |

Deliberately **not** tagged with `messageType` — with several interfaces × several message types
each, a third high-cardinality dimension risked unbounded Prometheus label cardinality;
`interfaceName` is enough to slice by.

## traffic-tester-app details

### Scenario configuration

Loaded from env var `TRAFFIC_TESTER_CONFIG` (default `./config/tester-scenario.yml`):

```yaml
udp:
  host: 127.0.0.1
  port: 5001

listener:
  enabled: true
  port: 7001
  durationSeconds: 120
  bufferSizeBytes: 65507

messages:
  - mode: FRUIT_ORANGE   # see PayloadMode for every mode; TEXT/BASE64/HEX send raw payloads
    target:               # optional per-message override of udp.host/port
      host: 127.0.0.1
      port: 5001
      transport: UDP       # optional — "UDP" (default) or "TCP"
    fruit:
      sourceFarm: "north-farm-17"
      freshness: "very_fresh"
  - mode: CANDY
    target:
      host: 127.0.0.1
      port: 5004
      transport: TCP
    candy:
      name: "chocolate-bar"
      calories: 250.5
  - mode: RADA_STATUS      # Rada messages need no per-message config — fields are Instancio-randomized
    target:
      host: 127.0.0.1
      port: 5050

repeat: 1231
intervalMillis: 1000
```

### Behavior

- **Send loop**: for `repeat` iterations, encode and send every entry in `messages` to its
  resolved target over the resolved transport, sleeping `intervalMillis` between iterations.
- **Listener**: if `listener.enabled`, a background thread binds `listener.port` **over UDP
  only** (there is no TCP listener in `traffic-tester-app`), logs each received packet, and
  attempts a best-effort Fruit/Weather decode.

### Standalone run

```bash
mvn -pl traffic-tester-app -am clean package -DskipTests
TRAFFIC_TESTER_CONFIG=config/tester-scenario.yml \
  java -jar traffic-tester-app/target/traffic-tester-app-1.0-SNAPSHOT.jar
```

## Running the project

```bash
docker compose up --build traffic-monitor-app
```

Open `http://localhost:8080`. In another terminal, run the tester (sends whatever
`config/tester-scenario.yml` defines):

```bash
docker compose --profile tester up --build traffic-tester-app
```

Exposed ports: `8080/tcp` (HTTP+UI+REST+`/actuator/*`), `5001/udp` (Fruit), `5002/udp` (Ping),
`5003/udp` (Weather), `5004/tcp` (Candy), `5050/udp` (Rada), `5051/udp` (Rada Little-Endian),
`5060/tcp` (Pets REST interface), `7001/udp` (tester listener). If you add a new interface, add
its port to `docker-compose.yml` too — for a REST interface, also make sure `./swagger` is
bind-mounted (it already is, alongside `./config`) so the container can see your swagger file.

The `./data` host directory is bind-mounted and holds the H2 database file — survives
`docker compose down`/`up`. Delete `./data` manually to start with an empty history.

```bash
curl http://localhost:8080/actuator/health
```

## Development

```bash
mvn clean package
```

builds all 3 modules in dependency order. Run the monitor locally without Docker:

```bash
TRAFFIC_TOOL_CONFIG=config/traffic-tool.yml \
  java -jar traffic-monitor-app/target/traffic-monitor-app-1.0-SNAPSHOT-exec.jar
```

(Note the `-exec` classifier — `spring-boot-maven-plugin`'s repackage uses a classifier so the
plain jar stays the resolvable Maven dependency `traffic-tester-app` consumes, and the runnable
fat jar is the separate `-exec.jar`.) Run the tester locally: see
[Standalone run](#standalone-run) above.

## Known gaps

- **`traffic-tester-app`'s listener is UDP-only** — it can *send* over TCP but can't receive a
  TCP auto-reply from the monitor.
- **`BananaMessageHandler`/`TemperatureReadingMessageHandler`/`CandyMessageHandler` are empty
  stubs** — only `OrangeMessageHandler` and `PingMessageHandler` do anything on arrival.
- **`network_monitor.tcp.connections.active` is a single combined gauge**, not split per
  interface/port.
- Multi-select interface filtering in the Live/History UI tabs is still single-select in the
  History tab's dropdown (the sidebar's click-to-toggle filter on the Live tab is effectively
  multi-select already).
- **No custom handler support for REST messages** — REST operations have no backing Java class,
  so there's nothing for a `MessageArrivedHandler<T>`-style handler to type against; REST server
  mode's response is always the static configured/fallback body (see
  [REST auto-reply](#rest-auto-reply)), never programmable logic.
- **No periodic REST publish** — `PeriodicPublisherService` is hard-wired to the legacy flat
  `PublishRequest`/`MonitorPayloadFactory` path, which can't represent a REST operation. On-demand
  REST publish (REST Publisher UI / `POST /api/publisher/send`) works fully.
- **REST auto-reply bodies are fully static** — no variable interpolation/templating (e.g. can't
  echo a path parameter back into the configured response).
- **No HTTPS for REST** — REST client-mode calls are `http://` only, no TLS config surface.
- **`oneOf`/`anyOf` OpenAPI schemas collapse to their first alternative** rather than fully
  modeling a polymorphic request/response body in the REST Publisher's generated form.
- **Switching a UDP/TCP interface to `protocol: REST` at runtime doesn't work** (see
  [Interface runtime control](#interface-runtime-control)) — only interfaces already declared
  `protocol: REST` in config at startup are functional.
