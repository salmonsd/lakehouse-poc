FROM sbtscala/scala-sbt:eclipse-temurin-jammy-11.0.17_8_1.9.3_2.12.18

WORKDIR /usr/src/app

COPY . /usr/src/app

# Build Jar
RUN sbt assembly