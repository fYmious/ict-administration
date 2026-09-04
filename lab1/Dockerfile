FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src 
RUN mvn clean package -DskipTests

FROM tomcat:10.1-jre21 
RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war 

COPY opentelemetry-javaagent.jar /usr/local/tomcat/otel-agent.jar 

ENV JAVA_OPTS="-javaagent:/usr/local/tomcat/otel-agent.jar"
ENV OTEL_SERVICE_NAME="lab1"
ENV OTEL_TRACES_EXPORTER="otlp"
ENV OTEL_EXPORTER_OTLP_PROTOCOL="grpc"
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://jaeger:4317"
ENV OTEL_METRICS_EXPORTER="none"
ENV OTEL_LOGS_EXPORTER="none"

EXPOSE 8080
CMD ["catalina.sh", "run"]
