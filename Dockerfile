## Stage 1 : Build with GraalVM
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 AS build
WORKDIR /code
COPY --chown=quarkus:quarkus pom.xml /code/
# Run dependency download as root to ensure mvn is on the path
RUN mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
COPY --chown=quarkus:quarkus src /code/src
# Build native image as root
RUN mvn package -Dnative -DskipTests

## Stage 2 : Create minimal runtime image
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build --chown=1001:root /code/target/*-runner /work/application

# Create cache directory and set permissions
RUN mkdir -p /work/cache && chown -R 1001:root /work/cache

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
