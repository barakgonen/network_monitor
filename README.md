# Traffic Interface Tool - Reflection Publisher Clean

This project contains no hardcoded Fruit/Weather/demo protocol classes.

It is intended to work with your own protocol classes/JARs configured through reflection.

## Modules

```text
shared-schemas
traffic-monitor-app
traffic-tester-app
```

## What remains

```text
Generic reflection monitoring
Opcode-routed receiving
Dynamic backend-driven Interfaces panel
Per-interface start/stop listener control
Multi-interface filtering
Backend formatted observedAtDisplay
Generic reflection publisher V1
Generic UDP tester with TEXT / BASE64 / HEX
```

## What was removed

```text
com.example.schemas.fruit.*
com.example.schemas.weather.*
com.example.schemas.reflectiondemo.*
Hardcoded Fruit/Weather publisher code
Hardcoded Fruit/Weather tester payloads
```

## Configure your own interfaces

Edit:

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

## Monitoring flow

```text
UDP packet arrives
→ calculate fixed header size from header-type
→ slice header bytes
→ parse header
→ read opcode-field-name
→ route to supported-messages[opcode]
→ parse exact message-class
→ validate full ByteBuffer consumption
→ display message body in UI
```

## Publisher V1

APIs:

```http
GET  /api/publisher/interfaces
POST /api/publisher/send
```

Publisher flow:

```text
UI selects configured interface
→ UI selects opcode/message
→ user edits JSON fields
→ backend creates message via no-arg constructor
→ backend applies fields recursively by reflection
→ backend serializes using supported serializer method
→ backend sends UDP packet
```

Supported serializer methods:

```java
toByteArray(ByteBuffer)
serialize(ByteBuffer)
writeTo(ByteBuffer)
toByteArray() -> byte[]
serialize() -> byte[]
```

## Required shape for publishable classes

```text
1. no-arg constructor
2. fields can be set by reflection
3. serializer method exists
```

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


## Publisher configuration-driven UI

The publisher view now lists only interfaces and messages from configuration.

Source:

```http
GET /api/publisher/interfaces
```

If no `traffic.reflection-interfaces` are configured, the UI shows an empty state instead of hardcoded demo messages.

Displayed metadata per interface:

```text
interface name
protocol
port
header type
opcode field
supported messages
```


## UI script stabilization

The UI script was rebuilt as one clean global script.

Fixes:

```text
showTab is globally defined
publisher functions are globally defined
inline onclick handlers resolve correctly
DOMContentLoaded initializes listeners safely
```


## Publisher generated field form

Publisher metadata now includes reflected message fields.

The UI renders:

```text
primitive/string fields -> input
numeric fields          -> number input
boolean fields          -> true/false dropdown
enum fields             -> dropdown with all enum constants
nested objects          -> grouped fields
arrays                  -> Advanced JSON payload in V1
```

The backend still sends the same request shape:

```json
{
  "interfaceName": "Rada Interface",
  "opcode": "4444",
  "host": "localhost",
  "port": 5050,
  "fields": {
    "header": {
      "msgType": 4444
    }
  }
}
```


## Publisher complex array field editor

Publisher field metadata now tries to infer array length from the message class no-arg constructor.

Example:

```java
private RadaPlotData[] plotData = new RadaPlotData[10];
```

If the array is initialized in the no-arg constructor or field initializer, the UI renders:

```text
plotData[0]
  fieldA
  fieldB
...
plotData[9]
  fieldA
  fieldB
```

Generated JSON shape:

```json
{
  "plotData": [
    { "fieldA": 0, "fieldB": 0 },
    { "fieldA": 0, "fieldB": 0 }
  ]
}
```

If the array is null or the length cannot be inferred, the UI falls back to Advanced JSON payload for that array field.


## Publisher collapsible dynamic array editor

Large fixed-size arrays are no longer expanded immediately.

Behavior:

```text
array field shows collapsed group
+ Add row adds one element
Remove removes an element
counter shows current / max rows
Add row is disabled at max size
```

Example:

```text
int[200]
```

renders as:

```text
myArray [max 200]
0 / 200 rows
+ Add row
```

The UI generates only the rows the user adds. Backend validation rejects payloads that exceed the inferred fixed max size. Existing/default array entries are preserved or initialized by the publisher backend.


## Publisher array editor refinement

Array editor now includes:

```text
+ Add row
− Remove last
per-row Remove
```

Behavior:

```text
Add row is disabled at max size
Remove last is disabled when empty
counter turns red when current rows == max rows
```


## Publisher UX: wide editor layout fixed

The Sample Publisher tab now gives most of the screen to the message editor while preserving all field-form JavaScript.

The `Selected message metadata` card is hidden rather than deleting required DOM/JS dependencies.


## Two-page UI redesign

The application UI is now structured as two main pages:

```text
Live Messages
Sample Publisher
```

Live Messages page:

```text
left side  -> ingestion status + dynamic interfaces list
center     -> live message table + payload inspector
```

Sample Publisher page:

```text
configured interfaces/messages
message selection
generated field editor
array row editor
send controls
```

The publisher page no longer shares the live monitoring sidebar, so the user has more room to build message content.


## Header cleanup

Removed the static header transport pill:

```html
<div class="app-header-right">
  <div class="stat-pill">
    <strong>UDP</strong>
    <span>transport</span>
  </div>
</div>
```

The app header now focuses on:
- product identity
- main page navigation


## Unified page layout

The UI now uses the same hierarchy for every page:

```text
Top left: app identity/icon
Top center: main page buttons
Then: page title and description
Below: unique page content
```

Live Messages and Sample Publisher now share the same title block treatment.


## Publisher selection UX

The Build and Send Message form no longer contains duplicate interface/message dropdowns.

Selection now happens only from the left `Configured Interfaces and Messages` panel:

```text
click interface name -> selects first message in that interface
click message chip   -> selects exact message
```

The Build and Send form shows a read-only selected message summary and renders the relevant fields.


## Publisher selected-message summary removed

Removed the selected message summary row from the Build and Send Message form.

Selection remains visible in the left Configured Interfaces and Messages panel.


## Publisher target input UX

Target Host and Target UDP Port inputs now:

```text
- center-align their text
- clear their initial content on first focus/type
```


## Publisher target input default restore

Target Host and Target UDP Port now:

```text
- clear on first focus/type
- restore the default value if left empty on blur
```

For Target UDP Port, the default is updated whenever the selected interface changes.


## Publisher JSON two-way sync

Advanced JSON payload now syncs back into the generated form.

Behavior:

```text
form input changes -> updates Advanced JSON
Advanced JSON changes -> updates generated form inputs
Advanced JSON array values -> rebuild dynamic array rows
invalid JSON -> shows sync error and does not update form
array exceeds max -> ignores extra rows and shows sync error
```


## Advanced JSON payload editor styling

The Advanced JSON payload textarea now uses a darker themed background instead of a bright white background.


## Advanced JSON payload editor styling fix

The Advanced JSON payload textarea now has a targeted `#publisherFieldsJson` dark-theme override and inline fallback to prevent global textarea styles from overriding it.

## Publisher backend test suite

Added unit/integration tests under:

```text
traffic-monitor-app/src/test/java/com/example/monitor/publisher
```

Covered cases:

```text
- metadata discovery for nested objects, enums and fixed complex arrays
- recursive JSON field application to objects and arrays
- array max-size validation
- ByteBuffer serializer contract
- direct byte[] serializer contract for complex array messages
- end-to-end PublisherService UDP send to a local DatagramSocket
- publisher metadata API service shape
```

Run locally:

```bash
mvn -pl traffic-monitor-app -am test
```

Note: I could not execute Maven in this sandbox because Maven/Docker are not installed in the execution environment.


## schema-utils module

Added Maven module:

```text
schema-utils
```

It contains two static utility classes:

```java
com.example.schemautils.ReflectionStructSizeCalculator
com.example.schemautils.StructSizeCalculator
```

Usage:

```java
int size = ReflectionStructSizeCalculator.calculateStructSize("com.example.MyHeader");
int size2 = StructSizeCalculator.calculateStructSize(MyHeader.class);
```

Fixed array support:

```java
@StructSizeCalculator.FixedArrayLength(10)
private MyItem[] items;
```

`traffic-monitor-app` now depends on `schema-utils` and uses these utilities for:
- header size calculation
- publisher ByteBuffer allocation
