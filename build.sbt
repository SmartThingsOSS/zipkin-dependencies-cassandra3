val V = new {
  val scala = "2.11.11"
  val spark = "2.1.0"
  val sparkCassandra = "2.0.1"
  val zipkin = "1.23.0"
  val slf4j = "1.7.25"
}

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt, DockerPlugin)
  .settings(
    organization := "com.smartthings",
    name := "zipkin-dependencies-cassandra3",
    scalaVersion := V.scala,
//    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
//      prependShellScript = Some(Seq("#!/usr/bin/env sh", """exec java $JAVA_OPTS -jar $0 $@"""))
//    ),

    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-core" % V.spark,
      "org.apache.spark" %% "spark-sql" % V.spark,
      "com.datastax.spark" %% "spark-cassandra-connector-unshaded" % V.sparkCassandra,
      "io.zipkin.java" % "zipkin" % V.zipkin,
      "io.zipkin.java" % "zipkin-storage-cassandra3" % V.zipkin,
      "com.typesafe" % "config" % "1.3.1",
      "com.github.kxbmap" %% "configs" % "0.4.4",
      "com.github.scopt" %% "scopt" % "3.5.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "org.slf4j" % "slf4j-api" % V.slf4j,
      "org.slf4j" % "log4j-over-slf4j" % V.slf4j,
      "org.slf4j" % "jul-to-slf4j" % V.slf4j,
      "org.slf4j" % "jcl-over-slf4j" % V.slf4j,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.datastax.cassandra" % "cassandra-driver-core" % "3.2.0",
      "com.datastax.cassandra" % "cassandra-driver-mapping" % "3.2.0",
      "io.netty" % "netty-all" % "4.0.44.Final",

      "io.zipkin.java" % "zipkin" % V.zipkin % "test" classifier "tests",
      "io.zipkin.java" % "zipkin-storage-cassandra3" % V.zipkin % "test",
      "junit" % "junit" % "4.12" % "test",
      "org.assertj" % "assertj-core" % "3.6.2" % "test",
      "com.novocode" % "junit-interface" % "0.11" % "test"
    ).map(_
      .exclude("org.slf4j","slf4j-log4j12")
      .exclude("log4j", "log4j")),

    assemblyMergeStrategy in assembly := {
      case PathList("org","aopalliance", xs @ _*) => MergeStrategy.last
      case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
      case PathList("javax", "activation", xs @ _*) => MergeStrategy.last
      case PathList("org", "apache", xs @ _*) => MergeStrategy.last
      case PathList("com", "google", xs @ _*) => MergeStrategy.last
      case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
      case PathList("com", "codahale", xs @ _*) => MergeStrategy.last
      case PathList("com", "yammer", xs @ _*) => MergeStrategy.last
      case "about.html" => MergeStrategy.rename
      case "META-INF/ECLIPSEF.RSA" => MergeStrategy.last
      case "META-INF/mailcap" => MergeStrategy.last
      case "META-INF/mimetypes.default" => MergeStrategy.last
      case "plugin.properties" => MergeStrategy.last
      case "log4j.properties" => MergeStrategy.last
      case "META-INF/io.netty.versions.properties" => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },

    imageNames in docker := Seq(
      ImageName(
        namespace = Some("smartthingsoss"),
        repository = "zipkin-dependencies-cassandra3",
        tag = Some(version.value)
      ),
      ImageName(
        namespace = Some("smartthingsoss"),
        repository = "zipkin-dependencies-cassandra3",
        tag = Some("latest")
      )
    ),

    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("openjdk:8-jre")
        add(artifact, artifactTargetPath)
        entryPointShell("java", "$JAVA_OPTS", "-jar", artifactTargetPath)
      }
    }
  )
