package smartthings.zipkin.dependencies.cassandra3.config

import com.typesafe.config.{Config => TypesafeConfig}
import scopt.OptionParser


case class Cassandra(contactPoints: String, port: Int = 9042, keyspace: String, username: String = "", password: String = "")

case class Spark(name: String = "Cassandra 3 Dependencies", master: String = "local[*]")

case class Dependencies(day: Option[Long] = None, spark: Spark, cassandra: Cassandra)

case class Config(dependencies: Dependencies)

object Config {

  import configs.syntax._

  lazy val parser = new OptionParser[Config]("zipkin-dependencies") {
    head("zipkin-dependencies", "0.1.x")
  }

  def apply(config: TypesafeConfig): Config = {
    Config(
      dependencies = config.get[Dependencies]("dependencies").value
    )
  }

  def apply(config: TypesafeConfig, args: Seq[String]): Option[Config] = {
    val base = Config(config)
    parser.parse(args, base)
  }

  def help(): Unit = parser.showUsageAsError()
}
