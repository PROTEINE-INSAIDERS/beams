name := "beams"

version := "0.1"

val akkaVersion = "2.6.1"
val zioVersion = "1.0.0-RC17"

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
 // "-Xfatal-warnings",
 // "-deprecation",
  "-Ypartial-unification")

lazy val commonSettings = Seq(
  scalaVersion := "2.12.10",
  scalacOptions ++= commonScalacOptions,
  licenses += ("BSD-3-Clause", url("http://opensource.org/licenses/BSD-3-Clause"))
)

lazy val beams = (project in file("beams")).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "dev.zio" %% "zio" % zioVersion,
    "com.twitter" %% "chill-akka" % "0.9.3"
  )
)

lazy val shards = (project in file("shards")).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.xerial.larray" %% "larray" % "0.4.1"
  )
)

lazy val mapReduceExample = (project in file("examples/map-reduce"))
  .settings(commonSettings)
  .dependsOn(beams)

lazy val helloWorld = (project in file("examples/hello-world"))
  .settings(commonSettings)
  .dependsOn(beams)

lazy val examples = (project in file("examples"))
  .aggregate(mapReduceExample, helloWorld)

lazy val root = (project in file("."))
  .aggregate(beams, examples)
  .enablePlugins(JavaAppPackaging)
