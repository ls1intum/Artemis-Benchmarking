FROM azul/zulu-openjdk:25.0.5.1-jdk AS build

# The Node.js binary that the Gradle node plugin downloads to build the webapp is linked against libatomic, which this
# base image does not contain ("node: error while loading shared libraries: libatomic.so.1"). Build stage only: the
# final image does not run Node.js.
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew -Pprod -Pwar --no-daemon clean bootwar

FROM azul/zulu-openjdk:25.0.5.1-jre

# wget is used by the docker-compose healthcheck but is not part of the JRE base image
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*

RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.war /app/app.war

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.war"]
