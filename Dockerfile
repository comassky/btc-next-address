# --- Étape 1 : Build Natif ---
FROM container-registry.oracle.com/graalvm/native-image:21 AS build

# Installation de Maven (car non présent dans l'image de base GraalVM)
RUN microdnf install -y maven shadow-utils

# Création d'un utilisateur pour ne pas build en root
RUN groupadd -g 1001 quarkus && useradd -u 1001 -g quarkus quarkus
USER quarkus
WORKDIR /code

# Copie des fichiers de dépendances
COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/

# Téléchargement des dépendances
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline

# Copie des sources
COPY --chown=quarkus:quarkus src /code/src

# Build natif Quarkus
RUN ./mvnw package -Dnative -Dquarkus.native.native-image-xmx=4g

# --- Étape 2 : Runtime ---
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

COPY --from=build /code/target/*-runner /work/application

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]