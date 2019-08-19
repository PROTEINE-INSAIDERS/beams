name := "beams"

version := "0.1"

val akkaVersion = "2.6.0-M5"

lazy val commonScalacOptions = Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  //  "-language:experimental.macros",
  "-language:postfixOps",
  "-unchecked",
  //"-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfatal-warnings",
  "-deprecation",
  "-Ypartial-unification")

lazy val commonSettings = Seq(
  scalaVersion := "2.12.8",
  scalacOptions ++= commonScalacOptions,
  licenses += ("BSD-3-Clause", url("http://opensource.org/licenses/BSD-3-Clause"))
)

lazy val beams = (project in file("beams")).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "org.scalaz" %% "scalaz-zio" % "1.0-RC5",
    "com.twitter" %% "chill-akka" % "0.9.3"
  )
)

lazy val mapReduceExample = (project in file("examples/map-reduce"))
  .settings(commonSettings)
  .dependsOn(beams)

lazy val clusterExample = (project in file("examples/cluster"))
  .settings(commonSettings)
  .dependsOn(beams)

lazy val examples = (project in file("examples"))
  .aggregate(mapReduceExample, clusterExample)

lazy val root = (project in file("."))
  .aggregate(beams, examples)
  .enablePlugins(JavaAppPackaging)
