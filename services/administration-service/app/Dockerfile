# Build context: the repository root.
#   docker build -f services/administration-service/app/Dockerfile .

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Maven itself goes into an image layer, not into the shared /root/.m2 cache: the images are
# built in parallel, and their wrappers would race to unpack Maven there.
ENV MAVEN_USER_HOME=/opt/maven-wrapper
RUN ./mvnw --batch-mode --version
# The parent POM lists every module, so all of them are copied.
COPY services services
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress \
      -pl services/administration-service/app -am package \
      -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/services/administration-service/app/target/administration-service.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness > /dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
