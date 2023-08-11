import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.avro.functions._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._
import org.apache.spark.sql.DataFrame
import io.delta.tables._
import io.confluent.kafka.schemaregistry.client.{
  SchemaRegistryClient,
  CachedSchemaRegistryClient,
  SchemaMetadata
}
import org.apache.logging.log4j.{LogManager, Logger}

object KafkaToBronzeDeltaTable extends App {
  // Set up SparkSession
  val spark = SparkSession
    .builder()
    .appName("KafkaToBronzeDeltaTable")
    .master("local[*]")
    .getOrCreate()

  import spark.implicits._

  // Initialize Log4j 2
  val logger: Logger = LogManager.getLogger(getClass.getName)

  val kafkaTopic = "dbserver1.inventory.customers"
  val subjectKey = s"${kafkaTopic}-key"
  val subjectValue = s"${kafkaTopic}-value"
  logger.info(s"Setting Kafka Topic: ${kafkaTopic}")

  // Define the Kafka parameters
  val kafkaParams = Map[String, String](
    "kafka.bootstrap.servers" -> "kafka:9092",
    "subscribe" -> kafkaTopic,
    "startingOffsets" -> "earliest" // Change this as per your requirement
  )

  // Define the schema registry URL
  val schemaRegistryUrl =
    "http://schema-registry:8081" // Update with the actual schema registry URL

  // Initialize the Schema Registry client
  val schemaRegistryClient: SchemaRegistryClient =
    new CachedSchemaRegistryClient(schemaRegistryUrl, 100)

  // Get the latest Avro schema from the schema registry
  val schemaMetadata: SchemaMetadata =
    schemaRegistryClient.getLatestSchemaMetadata(subjectValue)
  val avroValueSchema: String = schemaMetadata.getSchema

  // Delta Lake Locations
  val deltaBronzePath = "s3a://warehouse/bronze/inventory/customers"
  val checkpointBronzePath = s"${deltaBronzePath}/_checkpoints/"

  // Read data from Kafka using Spark Structured Streaming
  val kafkaStream = (
    spark.readStream
      .format("kafka")
      .options(kafkaParams)
      .load()
    )

  // Define the Avro schema retrieval UDF
  val getAvroSchemaId = udf { (avroBytes: Array[Byte]) =>
    val magicByte = avroBytes.head
    if (magicByte != 0x00) {
      throw new RuntimeException("Invalid magic byte in Avro message")
    }

    val schemaIdBytes = avroBytes.slice(1, 5)
    val schemaId = java.nio.ByteBuffer.wrap(schemaIdBytes).getInt
    schemaId
  }

  // Parse the stream, getting the schema id and fixed value
  val kafkaParsedStream = (
    kafkaStream
      .withColumn("value_schema_id", getAvroSchemaId(col("value")))
      .withColumn(
        "fixed_value",
        expr("substring(value,6)")
      )
    )

  def parseAvroDataWithSchemaId(
      microBatchOutputDF: DataFrame,
      batchId: Long
  ): Unit = {
    val cachedDf = microBatchOutputDF.cache()

    def getSchema(id: Int): String = {
      schemaRegistryClient.getSchemaById(id).toString
    }

    val distinctValueSchemaIdDF =
      cachedDf.select(col("value_schema_id").cast("integer")).distinct()

    for (valueRow <- distinctValueSchemaIdDF.collect()) {
      val currentValueSchemaId =
        spark.sparkContext.broadcast(valueRow.getAs[Int]("value_schema_id"))
      val currentValueSchema =
        spark.sparkContext.broadcast(getSchema(currentValueSchemaId.value))
      val filterValueDF =
        cachedDf.filter(col("value_schema_id") === currentValueSchemaId.value)

      filterValueDF
        .select(
          from_avro(col("fixed_value"), currentValueSchema.value).as(
            "parsedValue"
          )
        )
        .select("parsedValue.*")
        .withColumn(
          "_cdc_meta_source_ts_ms",
          expr("timestamp_millis(_cdc_meta_source_ts_ms)")
        )
        .write
        .format("delta")
        .mode("append")
        .save(deltaBronzePath)
    }

    cachedDf.unpersist(false)
  }

  val bronzeWriteStream = (
    kafkaParsedStream.writeStream
      .queryName("KafkaToBronze")
      .option(
        "checkpointLocation",
        checkpointBronzePath
      )
      .foreachBatch(parseAvroDataWithSchemaId _)
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .start()
    )

  bronzeWriteStream.awaitTermination()
}
