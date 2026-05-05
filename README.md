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
