FROM container-registry.oracle.com/graalvm/native-image:25 AS build
RUN microdnf install -y maven
WORKDIR /code
COPY mvnw /code/mvnw
COPY .mvn /code/.mvn
COPY pom.xml /code/
RUN chmod +x /code/mvnw
RUN ./mvnw -B org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline
COPY src /code/src
RUN ./mvnw package -Dnative -Dquarkus.native.native-image-xmx=4g

FROM registry.access.redhat.com/ubi9/ubi
WORKDIR /work/
RUN dnf install -y \
    freetype \
    fontconfig \
    libX11 \
    libXext \
    libXrender \
    libXtst \
    && dnf clean all

COPY --from=build /code/target/*-runner /work/application
RUN chown 1001 /work && chmod "g+rwX" /work && chown 1001:root /work

USER 1001
EXPOSE 8080

ENV LD_LIBRARY_PATH=/usr/lib64
CMD ["./application", "-Dquarkus.http.host=0.0.0.0", "-Djava.awt.headless=true"]