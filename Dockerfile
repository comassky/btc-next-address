# --- STEP 1: Build ---
FROM container-registry.oracle.com/graalvm/native-image:25 AS build
RUN microdnf install -y maven
WORKDIR /code
COPY . .
RUN ./mvnw package -Dnative

# --- STEP 2: Runtime (The "Top" Micro Image) ---
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
RUN chown 1001 /work && chmod "g+rwX" /work && chown 1001:root /work
USER 1001
EXPOSE 8080
# No -Djava.awt.headless=true needed anymore!
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]