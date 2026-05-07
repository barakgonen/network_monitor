FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl traffic-monitor-app -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/traffic-monitor-app/target/traffic-monitor-app-*.jar /app/app.jar
EXPOSE 8080 5001/udp 5003/udp
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
