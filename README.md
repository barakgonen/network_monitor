# Traffic Interface Tool

Multi-module Java skeleton:

- `shared-schemas` - shared SPI/contracts
- `traffic-monitor-app` - monitor application
- `traffic-tester-app` - tester/publisher application

## Build locally

```bash
mvn clean package
```

## Run monitor with Docker Compose

```bash
docker compose up --build traffic-monitor-app
```

Monitor UI:

```text
http://localhost:8080
```

UDP listener example:

```text
localhost:5001/udp
```

TCP listener example:

```text
localhost:5002
```

## Run tester profile

```bash
docker compose --profile tester up --build
```

## Configuration files

Runtime config is mounted from:

```text
./config
```
