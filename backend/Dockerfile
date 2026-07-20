FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
COPY --from=build /workspace/target/nr-ilcr-backend-*.jar /app/app.jar

EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
