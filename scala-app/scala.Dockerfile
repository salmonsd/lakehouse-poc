FROM sbtscala/scala-sbt:eclipse-temurin-jammy-11.0.17_8_1.9.3_2.12.18

# Set working directory inside the container
WORKDIR /usr/src/app

# Copy Scala project to the container
COPY . /usr/src/app

# Build
RUN sbt assembly