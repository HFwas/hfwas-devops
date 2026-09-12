# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,id=hfwas-m2-spi,target=/root/.m2/repository \
    mvn -B -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN --mount=type=cache,id=hfwas-m2-spi,target=/root/.m2/repository \
    mvn -B -DskipTests package \
    && cp target/hfwas-keycloak-http-listener-*.jar /tmp/hfwas-keycloak-http-listener.jar

FROM busybox:1.37
COPY --from=build /tmp/hfwas-keycloak-http-listener.jar /jar/hfwas-keycloak-http-listener.jar
CMD ["cp", "/jar/hfwas-keycloak-http-listener.jar", "/out/hfwas-keycloak-http-listener.jar"]
