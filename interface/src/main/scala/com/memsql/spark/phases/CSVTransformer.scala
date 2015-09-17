package com.memsql.spark.phases

import com.memsql.spark.etl.api.{ByteArrayTransformer, PhaseConfig}
import com.memsql.spark.etl.utils.{PhaseLogger, SimpleJsonSchema}
import org.apache.commons.csv._
import org.apache.spark.rdd._
import org.apache.spark.sql._
import spray.json.JsValue

import scala.collection.JavaConversions._

case class CSVTransformerConfig(
  delimiter: Option[Char],
  escape: Option[Char],
  quote: Option[Char],
  null_string: Option[String],
  columns: JsValue) extends PhaseConfig

class CSVTransformer extends ByteArrayTransformer {
  override def transform(sqlContext: SQLContext, rdd: RDD[Array[Byte]], transformConfig: PhaseConfig, logger: PhaseLogger): DataFrame = {
    val config = transformConfig.asInstanceOf[CSVTransformerConfig]
    val csvFormat = getCSVFormat(config)
    val nullString = config.null_string
    val schema = SimpleJsonSchema.jsonSchemaToStruct(config.columns)

    val parsedRDD = rdd.map(byteUtils.bytesToUTF8String)
                       .flatMap(parseCSVLines(_, csvFormat))

    val nulledRDD = nullString match {
      case Some(nullS) => parsedRDD.map(x => x.map(y => if (y.trim() == nullS) None else y))
      case None => parsedRDD
    }

    val rowRDD = nulledRDD.map(x => Row.fromSeq(x))
    return sqlContext.createDataFrame(rowRDD, schema)
  }

  private def getCSVFormat(config: CSVTransformerConfig): CSVFormat = {
    // The MYSQL format is a sensible base format (you need to pick one in CSVFormat). We
    // override most of the options via the config, so the choice is not too significant.
    return CSVFormat.MYSQL
      .withDelimiter(config.delimiter.getOrElse(','))
      .withEscape(config.escape.getOrElse('\\'))
      .withQuote(config.quote.getOrElse('"'))
      .withIgnoreSurroundingSpaces()
  }

  // TODO: support non-standard line delimiters
  private def parseCSVLines(s: String, format: CSVFormat): Iterable[List[String]] = {
    return CSVParser.parse(s, format).map(record => record.toList)
  }
}
