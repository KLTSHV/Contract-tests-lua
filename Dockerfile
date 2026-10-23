FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY contracts ./contracts
COPY examples ./examples
RUN mvn -B -ntp clean verify

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/contract-lab.jar ./contract-lab.jar
COPY contracts ./contracts
COPY examples ./examples
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/contract-lab.jar"]
CMD ["--contract", "contracts/user.lua", "--file", "examples/user.json"]
