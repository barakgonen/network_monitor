FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl traffic-monitor-app -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data
COPY --from=build /workspace/traffic-monitor-app/target/traffic-monitor-app-*-exec.jar /app/app.jar
EXPOSE 8080 5001/udp 5002/udp 5003/udp 5004/tcp 5005/udp 5050/udp 5051/udp 5060/tcp
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
