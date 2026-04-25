FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/doc-extraction-app-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
