# Traffic Interface Tool

Initial runnable version.

## Modules

- `shared-schemas` - shared contracts/SPI
- `traffic-monitor-app` - Spring Boot monitor app
- `traffic-tester-app` - UDP tester app

## Run monitor

```bash
docker compose up --build traffic-monitor-app
```

Open:

```text
http://localhost:8080
```

## Send UDP message from tester

In another terminal:

```bash
docker compose --profile tester up --build traffic-tester-app
```

## Check monitor logs

```bash
docker logs -f traffic-monitor-app
```

Expected log:

```text
Received UDP packet ...
```

## Check recent messages API

```bash
curl http://localhost:8080/api/messages/recent
```

## Tester payload config

Edit:

```text
config/tester-scenario.yml
```

Example:

```yaml
udp:
  host: traffic-monitor-app
  port: 5001

payload:
  mode: TEXT
  text: "hello from traffic-tester-app over UDP"

repeat: 1
intervalMillis: 1000
```
