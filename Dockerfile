#Build (Native Java 25) ---
FROM container-registry.oracle.com/graalvm/native-image:25 AS build
WORKDIR /code
COPY . .
RUN chmod +x mvnw && ./mvnw package -Dnative

#Runtime
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
USER root
RUN mkdir -p /data && chown 1001:root /data && chmod 775 /data
USER 1001

EXPOSE 8080
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]