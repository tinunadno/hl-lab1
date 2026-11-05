# One Dockerfile for every service: docker build --build-arg MODULE=user-service .
# --- build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /app
COPY . .
# The shared cache keeps the seven builds from downloading dependencies seven
# times; "locked" is needed because Maven's local repository isn't safe for
# concurrent writes when compose builds images in parallel.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -B -pl "$MODULE" -am clean package -DskipTests \
    && cp "$MODULE"/target/"$MODULE"-*.jar /app.jar

# --- run stage ---
FROM eclipse-temurin:17-jre
# curl is used by the compose health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "app.jar"]
