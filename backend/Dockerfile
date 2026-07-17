FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
COPY --from=build /workspace/target/nr-ilcr-backend-*.jar /app/app.jar
COPY --from=build /workspace/target/classes/ca/bc/gov/nrs/ilcr/HealthCheck.class /app/healthcheck/ca/bc/gov/nrs/ilcr/HealthCheck.class

EXPOSE 8080
USER 65532:65532
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD ["java", "-XX:MaxRAM=64m", "-cp", "/app/healthcheck", "ca.bc.gov.nrs.ilcr.HealthCheck"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
