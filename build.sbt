name := "loggingexperiment"

version := "0.1"

scalaVersion := "2.12.10"

lazy val Version = new {
  val circe = "0.11.1"
  val slf4j = "1.7.29"
  val logback = "1.2.3"
  val logstashLogback = "6.2"
  val zio = "1.0.0-RC16"
  val monix = "3.1.0"
}

lazy val root = project
  .in(file("."))
  .settings(
    name := "loggingexperiment",
    publish / skip := true, // doesn't publish ivy XML files, in contrast to "publishArtifact := false"
  )
  .aggregate(
    logbackZio,
  )

lazy val logbackZio = project
  .in(file("logback-zio"))
  .settings(
    name := "logback-zio",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % Version.slf4j,
      "ch.qos.logback" % "logback-classic" % Version.logback,
      "net.logstash.logback" % "logstash-logback-encoder" % Version.logstashLogback,
      "io.circe" %% "circe-core" % Version.circe,
      "io.circe" %% "circe-generic" % Version.circe,
      "dev.zio" %% "zio" % Version.zio,
    )
  )

lazy val logbackMonix = project
  .in(file("logback-monix"))
  .settings(
    name := "logback-monix",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % Version.slf4j,
      "ch.qos.logback" % "logback-classic" % Version.logback,
      "net.logstash.logback" % "logstash-logback-encoder" % Version.logstashLogback,
      "io.circe" %% "circe-core" % Version.circe,
      "io.circe" %% "circe-generic" % Version.circe,
      "io.monix" %% "monix" %  Version.monix,
    )
  )
