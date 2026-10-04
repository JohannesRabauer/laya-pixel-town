# Pixel Town app image: build with Maven, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --uid 10002 --create-home pixeltown
USER pixeltown
WORKDIR /app
COPY --from=build /src/target/pixel-town-*.jar /app/pixel-town.jar
EXPOSE 8765
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8765 && printf 'GET /api/defaults HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q ' 200' <&3"]
ENTRYPOINT ["java", "-jar", "/app/pixel-town.jar"]
