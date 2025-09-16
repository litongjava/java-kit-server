FROM litongjava/java-manimce:0.19.0
WORKDIR /app
COPY target/java-kit-server-1.0.0.jar /app/
CMD ["java", "-jar", "java-kit-server-1.0.0.jar"]