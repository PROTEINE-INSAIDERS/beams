name := "beams"

version := "0.1-SNAPSHOT"

val akkaVersion = "2.6.0-M7"
val zioVersion = "1.0-RC5"

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
  scalaVersion := "2.12.9",
  scalacOptions ++= commonScalacOptions,
  licenses += ("BSD-3-Clause", url("http://opensource.org/licenses/BSD-3-Clause"))
)

lazy val beams = (project in file("beams")).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "org.scalaz" %% "scalaz-zio" % zioVersion,
    "com.twitter" %% "chill-akka" % "0.9.3"
  )
)

lazy val helloWorld = (project in file("examples/hello-world"))
  .settings(commonSettings)
  .dependsOn(beams)

lazy val examples = (project in file("examples"))
  .aggregate(helloWorld)

lazy val root = (project in file("."))
  .aggregate(beams, examples)
  .enablePlugins(JavaAppPackaging)
