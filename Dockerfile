# TripLedger app image. Two stages: build the jar with Maven, then run it on a small JRE image.
# Tests are NOT run here (they need a database) - CI runs them on every push.

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY core core
COPY app app
# the cache mount keeps downloaded dependencies between builds, so only the first build is slow
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# never run as root inside the container
RUN useradd --system --uid 10001 tripledger
COPY --from=build /src/app/target/app-0.1.0-SNAPSHOT.jar app.jar
USER tripledger
EXPOSE 8080
# use up to 75% of the container's memory limit for the heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
