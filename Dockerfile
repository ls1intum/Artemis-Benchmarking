FROM azul/zulu-openjdk:25.0.3-jdk AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew -Pprod -Pwar --no-daemon clean bootwar

FROM azul/zulu-openjdk:25.0.3-jre

# wget is used by the docker-compose healthcheck but is not part of the JRE base image
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*

RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.war /app/app.war

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.war"]
