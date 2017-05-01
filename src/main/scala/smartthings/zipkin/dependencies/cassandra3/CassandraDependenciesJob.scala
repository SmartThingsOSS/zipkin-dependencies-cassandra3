package smartthings.zipkin.dependencies.cassandra3

import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util
import java.util.concurrent.TimeUnit
import java.util.{Date, TimeZone}

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector
import com.typesafe.scalalogging.StrictLogging
import org.apache.spark.{SparkConf, SparkContext}
import smartthings.zipkin.dependencies.cassandra3.config.Dependencies
import zipkin.internal.ApplyTimestampAndDuration.guessTimestamp
import zipkin.internal.Util.midnightUTC
import zipkin.internal.{CorrectForClockSkew, DependencyLinker}
import zipkin.storage.cassandra3.SparkSchema.{Annotation, BinaryAnnotation, TraceId}
import zipkin.{Codec, DependencyLink, Span}

import scala.collection.JavaConversions._
import scala.util.{Failure, Try}

object CassandraDependenciesJob {

  def apply(config: Dependencies): CassandraDependenciesJob = {
    new CassandraDependenciesJob(config)
  }

  implicit object SpanListOrdering extends Ordering[List[Span]] {
    override def compare(x: List[Span], y: List[Span]): Int = (x, y) match {
      case (Nil, Nil) => 0
      case (Nil, _) => 1
      case (_, Nil) => -1
      case (xHead :: _, yHead :: _) => xHead.compareTo(yHead)
    }
  }

  def rowsToLinks(startTs: Long, endTs: Long)(rows: Iterable[CassandraRow]): Iterable[DependencyLink] = {
    val spans = rows.map(rowToSpan)

    val linker = new DependencyLinker()

    val groupedSpans = spans
      .groupBy(span => TraceId(span.traceIdHigh, span.traceId))
      .mapValues(spans => CorrectForClockSkew.apply(spans.toList).toList)
      .values.toList.sorted

    val results = groupedSpans.filter { spans =>
      val ts = guessTimestamp(spans.head)
      !(ts == null || ts < startTs || ts > endTs)
    }

    results.foreach(spans => linker.putTrace(spans))

    linker.link()
  }

  private def rowToSpan(row: CassandraRow): Span = {

    val traceId = row.get[TraceId]("trace_id")

    Span.builder()
      .name(row.get[Option[String]]("span_name").orNull)
      .annotations(row.get[Option[List[Annotation]]]("annotations").getOrElse(List()).map(_.toAnnotation))
      .binaryAnnotations(row.get[Option[List[BinaryAnnotation]]]("binary_annotations").getOrElse(List()).map(_.toBinaryAnnotation))
      .duration(row.get[Option[java.lang.Long]]("duration").orNull)
      .id(row.getLong("id"))
      .parentId(row.get[Option[java.lang.Long]]("parent_id").orNull)
      .timestamp(row.get[Option[java.lang.Long]]("ts").orNull)
      .traceId(traceId.low)
      .traceIdHigh(traceId.high)
      .build()
  }

}

class CassandraDependenciesJob(config: Dependencies) extends StrictLogging {

  import CassandraDependenciesJob._
  import zipkin.storage.cassandra3.SparkSchema._

  def start(): Unit = {
    val sparkConfig = new SparkConf()
      .setAppName(config.spark.name)
      .setMaster(config.spark.master)
      .setAll(Map(
        "spark.ui.enabled" -> "false",
        "spark.cassandra.connection.host" -> config.cassandra.contactPoints,
        "spark.cassandra.connection.port" -> config.cassandra.port.toString,
        "spark.cassandra.auth.username" -> config.cassandra.username,
        "spark.cassandra.auth.password" -> config.cassandra.password
      ))

    val day =  midnightUTC(config.day.getOrElse(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)))


    val microsLower = day * 1000
    val microsUpper = microsLower + TimeUnit.DAYS.toMicros(1) - 1

    logger.info(s"Running Dependencies job for ${dateStamp(day)}: $microsLower ≤ Span.timestamp $microsUpper ")

    val context = new SparkContext(sparkConfig)

    val links = Try {
      context.cassandraTable(config.cassandra.keyspace, "traces")
        .spanBy(r => r.get[TraceId]("trace_id"))
        .flatMapValues(rowsToLinks(microsLower, microsUpper)).values
        .map(link => ((link.parent, link.child), link))
        .reduceByKey((l, r) => DependencyLink.create(l.parent, l.child, l.callCount + r.callCount)).values
        .collect()
    }

    context.stop()

    val results = links.map(l => save(day, l, sparkConfig))

    results match {
      case Failure(e) => logger.error("Link processing failed", e)
      case _ => logger.info("Link processing finished")
    }

  }



  private def save(day: Long, links: Array[DependencyLink], sparkConf: SparkConf): Unit = {
    val blob = ByteBuffer.wrap(Codec.THRIFT.writeDependencyLinks(util.Arrays.asList(links: _*)))

    logger.info(s"Saving with day=${dateStamp(day)}")

    CassandraConnector(sparkConf).withSessionDo { session =>
      session.execute(QueryBuilder.insertInto(config.cassandra.keyspace, "dependencies")
        .value("day", new Date(day))
        .value("links", blob)
      )
    }

    logger.info("Done")
  }

  private def dateStamp(day: Long): String = {
    val df = new SimpleDateFormat("yyyy-MM-dd")
    df.setTimeZone(TimeZone.getTimeZone("UTC"))
    df.format(new Date(day))
  }

}
