FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl sample-publisher-app -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/sample-publisher-app/target/sample-publisher-app-*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
