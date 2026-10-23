FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG SERVICE
WORKDIR /app
COPY services/${SERVICE}/target/${SERVICE}-1.0-SNAPSHOT.jar /app/service.jar

ENTRYPOINT ["java", "-jar", "/app/service.jar"]
