package zipkin.storage.cassandra3

import org.junit.AssumptionViolatedException
import zipkin.internal.LazyCloseable

import scala.util.{Failure, Success, Try}


object CassandraTestGraph {

  val storage = new LazyCloseable[Cassandra3Storage] {

    private lazy val value = {
      val result = Cassandra3Storage.builder()
        .keyspace("test_zipkin3")
        .build()
      val check = result.check()
      if (check.ok) Success(result)
      else Failure(new AssumptionViolatedException(check.exception.getMessage))
    }

    override def compute(): Cassandra3Storage = {
      value match {
        case Success(r) => r
        case Failure(e) => throw e
      }
    }
  }

}
