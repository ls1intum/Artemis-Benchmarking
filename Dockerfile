FROM eclipse-temurin:21.0.7_6-jdk AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew -Pprod -Pwar --no-daemon clean bootwar

FROM eclipse-temurin:21.0.7_6-jre

RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.war /app/app.war

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.war"]
