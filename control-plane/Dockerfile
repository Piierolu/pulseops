FROM maven:3.9.11-eclipse-temurin-21 AS dependencies
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline

FROM dependencies AS test
COPY src src
RUN mvn -B test

FROM dependencies AS build
COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 pulseops
WORKDIR /app
RUN mkdir /otel && chmod 755 /otel
ADD --chmod=444 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.18.1/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY --from=build /workspace/target/control-plane-*.jar app.jar
USER pulseops
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
