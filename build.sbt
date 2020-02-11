name := "beams"

version := "0.1"

val akkaVersion = "2.6.3"
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
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "dev.zio" %% "zio" % zioVersion
  )
)

lazy val helloWorld = (project in file("examples/hello-world"))
  .settings(commonSettings,
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.0.0-RC2",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.twitter" %% "chill-akka" % "0.9.5"
    ))
  .dependsOn(beams)

lazy val examples = (project in file("examples"))
  .aggregate(helloWorld)

lazy val root = (project in file("."))
  .aggregate(beams, examples)
  .enablePlugins(JavaAppPackaging)
