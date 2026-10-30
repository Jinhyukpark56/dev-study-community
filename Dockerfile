FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew clean test bootJar --no-daemon && mv build/libs/*.jar build/application.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/application.jar ./application.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
