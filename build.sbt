name := "beams"

version := "0.1-SNAPSHOT"

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
  scalaVersion := Versions.scala,
  scalacOptions ++= commonScalacOptions,
  licenses += ("BSD-3-Clause", url("http://opensource.org/licenses/BSD-3-Clause"))
)

lazy val beams = (project in file("beams")).settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.typesafe.akka" %% "akka-actor-typed" % Versions.akka,
    "com.typesafe.akka" %% "akka-cluster-typed" % Versions.akka,
    "org.scalaz" %% "scalaz-zio" % Versions.zio,
    "com.twitter" %% "chill-akka" % "0.9.3",
    "org.scalactic" %% "scalactic" % "3.0.8"
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
