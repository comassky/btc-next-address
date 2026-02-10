# Étape 1 : Build natif avec Mandrel (GraalVM optimisé pour Quarkus)
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder:23.1-jdk-21 AS build
USER quarkus
WORKDIR /code

# Copie des fichiers de dépendances pour le cache
COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/

# Téléchargement des dépendances (évite de tout retélécharger si le code change)
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline

# Copie des sources
COPY --chown=quarkus:quarkus src /code/src

# Compilation native
# On limite la RAM à 4g pour que le runner GitHub ne crash pas
RUN ./mvnw package -Dnative -Dquarkus.native.native-image-xmx=4g

# Étape 2 : Image finale légère
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.9
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

# Copie du binaire produit (le nom finit souvent par -runner)
COPY --from=build /code/target/*-runner /work/application

EXPOSE 8080
USER 1001

CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]