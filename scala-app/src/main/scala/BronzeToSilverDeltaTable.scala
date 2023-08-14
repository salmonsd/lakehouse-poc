import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._
import org.apache.spark.sql.DataFrame
import io.delta.tables._
import org.apache.logging.log4j.{LogManager, Logger}

object BronzeToSilverDeltaTable extends App {
  // Set up SparkSession
  val spark = SparkSession
    .builder()
    .appName("BronzeToSilverDeltaTable")
    .master("local[*]")
    .getOrCreate()

  import spark.implicits._

  // Initialize Log4j 2
  val logger: Logger = LogManager.getLogger(getClass.getName)

  // Delta Lake Locations
  val deltaBronzePath = "s3a://warehouse/bronze/inventory/customers"
  val deltaSilverPath = "s3a://warehouse/silver/inventory/customers"
  val checkpointSilverPath = s"${deltaSilverPath}/_checkpoints/"

  val maxExceptions = 3 // Maximum number of exceptions before exiting
  var exceptionCount = 0

  var isRunning = true

  while (isRunning && exceptionCount < maxExceptions) {
    try {
      val bronzeReadStream = (spark.readStream
        .format("delta")
        .load(deltaBronzePath))

      val deltaSilverTable = (DeltaTable
        .createIfNotExists(spark)
        .location(deltaSilverPath)
        .addColumns(bronzeReadStream.schema)
        .execute)

      // Function to upsert microBatchOutputDF into Delta table using merge
      def upsertToDelta(microBatchOutputDF: DataFrame, batchId: Long) = {
        microBatchOutputDF.cache()

        deltaSilverTable
          .as("t")
          .merge(microBatchOutputDF.as("s"), "s.id = t.id")
          .whenMatched("s._cdc_meta_op = 'd'")
          .delete()
          .whenMatched()
          .updateAll()
          .whenNotMatched()
          .insertAll()
          .execute()
      }

      // Write the output of a streaming aggregation query into Delta table
      val silverWriteStream = (bronzeReadStream.writeStream
        .format("delta")
        .queryName("BronzeToSilver")
        .option(
          "checkpointLocation",
          checkpointSilverPath
        )
        .foreachBatch(upsertToDelta _)
        .outputMode("update")
        .trigger(Trigger.ProcessingTime("10 seconds"))
        .start())

      silverWriteStream.awaitTermination()

      // If the query terminates successfully, set isRunning to false to exit the loop
      isRunning = false
    } catch {
      case e: Exception =>
        // Log the exception and increment the exception count
        logger.error("Caught exception", e)
        exceptionCount += 1
    }
  }
}
