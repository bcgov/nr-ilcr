FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package

FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d

WORKDIR /app
COPY --from=build /workspace/target/nr-ilcr-backend-*.jar /app/app.jar
COPY tls-legacy.security /app/tls-legacy.security

EXPOSE 8080
USER 65532:65532
# MaxRAM bounds what MaxRAMPercentage is taken from — without a container
# memory limit the JVM would otherwise size itself off the node's memory.
ENTRYPOINT ["java", "-XX:MaxRAM=512m", "-XX:MaxRAMPercentage=50", "-Djava.security.properties=/app/tls-legacy.security", "-jar", "/app/app.jar"]
