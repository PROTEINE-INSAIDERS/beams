name := "beams"

version := "0.1"

//scalaVersion := "2.11.12"

scalaVersion := "2.12.8"

val akkaVersion = "2.6.0-M4"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:experimental.macros",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfatal-warnings",
  "-deprecation",
  "-Ypartial-unification")

libraryDependencies ++= Seq(
  //"org.typelevel" %% "cats-core" % "2.0.0-SNAPSHOT",
  "org.typelevel" %% "cats-effect" % "2.0.0-M3",
  "io.monix" %% "monix" % "3.0.0-RC3",
  "org.typelevel" %% "cats-mtl-core" % "0.6.0",
  "com.carrotsearch" % "hppc" % "0.8.1",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.github.mpilquist" %% "simulacrum" % "0.19.0")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
