name := "spark-streaming-lakehouse"
version := "1.0"
scalaVersion := "2.12.18"

resolvers += "confluent" at "https://packages.confluent.io/maven/"

val sparkVersion = "3.4.0" // Change this to your desired Spark version
val confluentVersion = "7.0.1" // Change this to your desired Confluent version
val deltaLakeVersion = "2.4.0"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-avro" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion % "provided",
  "io.confluent" % "kafka-schema-registry-client" % confluentVersion,
  "io.confluent" % "kafka-avro-serializer" % confluentVersion,
  "io.delta" %% "delta-core" % deltaLakeVersion,
  "org.apache.logging.log4j" % "log4j" % "2.20.0" pomOnly()
)

assemblyMergeStrategy in assembly := {
  case x if x.endsWith("module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}