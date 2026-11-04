FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system filehome \
    && useradd --system --gid filehome --home-dir /app filehome

WORKDIR /app
COPY --from=build /workspace/target/filehome-backend-0.0.1-SNAPSHOT.jar /app/filehome-backend.jar
RUN mkdir -p /app/logs && chown -R filehome:filehome /app

USER filehome
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dfile.encoding=UTF-8", "-jar", "/app/filehome-backend.jar"]
