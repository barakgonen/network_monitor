FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl traffic-proxy-app -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/traffic-proxy-app/target/traffic-proxy-app-*.jar /app/app.jar
COPY --from=build /workspace/traffic-proxy-app/target/lib /app/lib
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
