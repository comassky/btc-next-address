FROM container-registry.oracle.com/graalvm/native-image:21 AS build
RUN microdnf install -y maven
WORKDIR /code
COPY mvnw /code/mvnw
COPY .mvn /code/.mvn
COPY pom.xml /code/
RUN chmod +x /code/mvnw
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
COPY src /code/src
RUN ./mvnw package -Dnative -Dquarkus.native.native-image-xmx=4g

FROM quay.io/quarkus/ubi9-quarkus-image:2.0
WORKDIR /work/
USER root
RUN microdnf install -y freetype fontconfig && microdnf clean all
COPY --from=build /code/target/*-runner /work/application
RUN chown 1001 /work && chmod "g+rwX" /work && chown 1001:root /work
USER 1001
EXPOSE 8080
CMD ["./application", "-Dquarkus.http.host=0.0.0.0", "-Djava.awt.headless=true"]