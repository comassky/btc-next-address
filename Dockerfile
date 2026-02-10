# --- Étape de Build ---
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder:23.1-jdk-21 AS build
USER quarkus
WORKDIR /code

# On copie d'abord le wrapper et le pom pour mettre les dépendances en cache
COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/

RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline

# On copie les sources
COPY --chown=quarkus:quarkus src /code/src

# Build natif (on limite la RAM pour GitHub Actions)
RUN ./mvnw package -Dnative -Dquarkus.native.native-image-xmx=4g

# --- Étape Runtime ---
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.9
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

COPY --from=build /code/target/*-runner /work/application

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]