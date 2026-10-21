# syntax=docker/dockerfile:1

# ───────────────────────────── build stage ─────────────────────────────
# Uses the committed Maven wrapper, so the image builds with exactly the Maven version
# this project is pinned to — not whatever a maven base image happens to ship today.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# The dependency manifest goes in FIRST, on its own layer. Docker caches every
# instruction and reuses it while its inputs are unchanged, so a code-only change
# skips the entire dependency download. Copy src/ first and you re-download the whole
# Maven tree on every edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

# Only the layers below this line are invalidated when source changes.
COPY src/ src/
# Tests are not run here on purpose: CI gates correctness (next step). An image build
# should be a fast, deterministic packaging step, not a test run.
RUN ./mvnw -B clean package -DskipTests

# ──────────────────────────── runtime stage ────────────────────────────
# A JRE, not a JDK — no compiler, no Maven, no source code. Smaller image, and a
# smaller attack surface because none of those tools exist to be exploited.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

# Never run as root in a container: a process escape should land on an account that
# can do nothing.
RUN addgroup -S pasheez && adduser -S -G pasheez pasheez
USER pasheez

# Documentation only — it publishes nothing by itself. The real port comes from $PORT.
EXPOSE 8080

# Two deliberate choices here:
#
# -XX:MaxRAMPercentage=75 — the JVM does read container memory limits, but defaults the
# heap to only 25% of them. On a 512MB instance that is a 128MB heap, which is needlessly
# tight. 75% leaves room for metaspace, thread stacks and GC overhead.
#
# The exec form (a JSON array, no shell) means the JVM is PID 1 and receives SIGTERM
# directly. In shell form a shell would be PID 1, swallow the signal, and the platform
# would SIGKILL us after its grace period — dropping connections instead of closing them.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
