FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY .mvn ./.mvn
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package

FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d

WORKDIR /app
COPY --from=build /workspace/target/nr-ilcr-backend-*.jar /app/app.jar
COPY tls-legacy.security /app/tls-legacy.security

EXPOSE 8080
USER 65532:65532
# MaxRAMPercentage is taken from the container memory limit (cgroup), set as
# MEMORY_LIMIT in openshift.deploy.yml — resize there, not here.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-Djava.security.properties=/app/tls-legacy.security", "-jar", "/app/app.jar"]
