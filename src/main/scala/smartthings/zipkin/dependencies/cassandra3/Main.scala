package smartthings.zipkin.dependencies.cassandra3

import com.typesafe.config.ConfigFactory
import smartthings.zipkin.dependencies.cassandra3.config.Config


object Main extends App {

  val config = Config(ConfigFactory.load(), args) match {
    case None => Config.help(); sys.exit(1)
    case Some(c) => c
  }


  CassandraDependenciesJob(config.dependencies).start()



}
