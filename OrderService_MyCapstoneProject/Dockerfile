# Build the jar first: mvn -DskipTests package  (or ./build-all.sh at the repo root)
ARG BASE_IMAGE=eclipse-temurin:17-jre
FROM ${BASE_IMAGE}

WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 8084
# Container-aware heap sizing instead of a fixed -Xmx
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
