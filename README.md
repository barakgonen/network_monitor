# Traffic Interface Tool

Initial runnable version with demo `Fruit Interface` protocol support.

## Demo Fruit protocol

Header:

```text
opcode:int32
sendTimeEpochMillis:int64
bodyLength:int32
```

Orange body:

```text
sourceFarmLength:int32
sourceFarm:utf8
freshness:byte
```

Freshness values:

```text
1 -> very_fresh
2 -> not_fresh
3 -> unknown
```

Orange opcode:

```text
1001
```

## Run monitor

```bash
docker compose up --build traffic-monitor-app
```

Open:

```text
http://localhost:8080
```

## Send Orange message from tester

In another terminal:

```bash
docker compose --profile tester up --build traffic-tester-app
```

## Change Orange message fields

Edit:

```text
config/tester-scenario.yml
```

Example:

```yaml
payload:
  mode: FRUIT_ORANGE
  fruit:
    sourceFarm: "north-farm-17"
    freshness: "very_fresh"
```

Supported freshness values:

```text
very_fresh
not_fresh
unknown
```

## Recent messages API

```bash
curl http://localhost:8080/api/messages/recent
```


## UI change

The live monitoring table now shows:

```text
Observed At | Protocol | Port | Interface | Message Name | Message Body | Parse Error
```

Raw Base64 was removed from the UI.


## Design system UI

The monitor UI now uses the dark System Flow Investigator style:
- sidebar
- topbar stats
- dark table layout
- message inspector panel
- filter input


## Banana message

Banana opcode:

```text
1002
```

Banana body:

```text
colorLength:int32
color:utf8
weight:double
```

Send Banana from tester:

```yaml
payload:
  mode: FRUIT_BANANA
  fruit:
    color: "yellow"
    weight: 142.75
```


## Multi-message tester scenario

The tester now supports sending multiple messages in one run.

Example:

```yaml
udp:
  host: traffic-monitor-app
  port: 5001

messages:
  - mode: FRUIT_ORANGE
    fruit:
      sourceFarm: "north-farm-17"
      freshness: "very_fresh"

  - mode: FRUIT_BANANA
    fruit:
      color: "yellow"
      weight: 142.75

repeat: 1
intervalMillis: 1000
```

Run:

```bash
docker compose --profile tester up --build traffic-tester-app
```

This sends Orange and Banana to the monitor over UDP.


## Weather Interface

A second demo interface was added.

```text
Weather Interface
UDP port: 5003
```

Message:

```text
TemperatureReading
opcode: 2001
```

Header:

```text
opcode:int32
sendTimeEpochMillis:int64
bodyLength:int32
```

Body:

```text
stationIdLength:int32
stationId:utf8
temperatureCelsius:double
condition:byte
```

Condition values:

```text
1 -> sunny
2 -> cloudy
3 -> rainy
4 -> unknown
```

The tester scenario now sends:

```text
Fruit Orange -> UDP 5001
Fruit Banana -> UDP 5001
Weather TemperatureReading -> UDP 5003
```


## Monitor Sample Publisher

The monitor app can now send UDP messages back using the same protocol codecs.

Open:

```text
http://localhost:8080
```

Go to:

```text
Sample Publisher
```

Supported messages:

```text
Fruit Interface / Orange
Fruit Interface / Banana
Weather Interface / TemperatureReading
```

Enum fields are rendered as dropdowns.

REST API:

```http
POST /api/publish/udp
Content-Type: application/json
```

Example:

```json
{
  "interfaceName": "Fruit Interface",
  "messageType": "Banana",
  "host": "localhost",
  "port": 5001,
  "fields": {
    "color": "yellow",
    "weight": 142.75
  }
}
```
