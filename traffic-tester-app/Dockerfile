FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY shared-schemas shared-schemas
COPY traffic-tester-app traffic-tester-app
RUN mvn -pl traffic-tester-app -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/traffic-tester-app/target/traffic-tester-app-*.jar /app/app.jar
COPY --from=build /workspace/traffic-tester-app/target/lib /app/lib
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
