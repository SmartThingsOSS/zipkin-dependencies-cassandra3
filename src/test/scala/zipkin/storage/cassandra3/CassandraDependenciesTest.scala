package zipkin.storage.cassandra3

import java.util

import smartthings.zipkin.dependencies.cassandra3.CassandraDependenciesJob
import smartthings.zipkin.dependencies.cassandra3.config.{Cassandra, Dependencies, Spark}
import zipkin.Span
import zipkin.internal.ApplyTimestampAndDuration.guessTimestamp
import zipkin.internal.Util.midnightUTC
import zipkin.internal.{CallbackCaptor, MergeById}
import zipkin.storage.{DependenciesTest, QueryRequest, StorageComponent}

import scala.collection.JavaConversions._


class CassandraDependenciesTest extends DependenciesTest {

  private implicit val ec = scala.concurrent.ExecutionContext.global

  private val _storage: Cassandra3Storage = CassandraTestGraph.storage.get()

  override def storage(): StorageComponent = _storage

  override def clear(): Unit = _storage.clear()

  override def processDependencies(spans: util.List[Span]): Unit = {
    val callback = new CallbackCaptor[Void]()
    _storage.asyncSpanConsumer().accept(spans, callback)
    callback.get()

    val traces = _storage.spanStore().getTraces(QueryRequest.builder().limit(10000).build())

    val days = traces
      .map(trace => midnightUTC(guessTimestamp(MergeById.apply(trace).get(0)) / 1000 ))

    val config = Dependencies(
      spark = Spark(),
      cassandra = Cassandra(
        contactPoints = "localhost",
        keyspace = "test_zipkin3"))

    days.foreach { day =>
      CassandraDependenciesJob(config.copy(day = Some(day))).start()
    }
  }

}
