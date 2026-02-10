# --- Stage 1: Build ---
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder:23.1-jdk-21 AS build
USER quarkus
WORKDIR /code

# Copie uniquement le nécessaire pour les dépendances
COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/

# Téléchargement des dépendances
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline

# Copie des sources et build
COPY --chown=quarkus:quarkus src /code/src
RUN ./mvnw package -Dnative -Dquarkus.native.native-image-xmx=4g

# --- Stage 2: Runtime ---
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.9
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
RUN chown 1001 /work && chmod "g+rwX" /work && chown 1001:root /work

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]