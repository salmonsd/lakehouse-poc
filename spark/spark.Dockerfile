FROM lakehouse-scala-apps:latest as scala-app

FROM bitnami/spark:3.4.0

USER 0

# Set envs
ENV SPARK_JAR_PATH /opt/bitnami/spark/jars
ENV SPARK_APPLICATION_JAR_NAME spark-streaming-lakehouse-assembly-1.0.jar
ENV SPARK_APP_BRONZE KafkaToBronzeDeltaTable
ENV SPARK_APP_SILVER BronzeToSilverDeltaTable
ENV SPARK_SUBMIT_BRONZE "--deploy-mode client --class $SPARK_APP_BRONZE $SPARK_JAR_PATH/$SPARK_APPLICATION_JAR_NAME"
ENV SPARK_SUBMIT_SILVER "--deploy-mode client --class $SPARK_APP_SILVER $SPARK_JAR_PATH/$SPARK_APPLICATION_JAR_NAME"

# Add Scala Jar to Spark Jar Path
COPY --chown=root:root --from=scala-app /usr/src/app/target/scala-2.12/$SPARK_APPLICATION_JAR_NAME $SPARK_JAR_PATH/$SPARK_APPLICATION_JAR_NAME