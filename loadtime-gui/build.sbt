name := """loadtime-gui"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  "junit" % "junit" % "4.11",
  "commons-io" % "commons-io" % "2.4",
  "com.google.guava" % "guava" % "18.0",
  "org.mongodb" % "mongo-java-driver" % "2.13.0",
  "com.typesafe" % "config" % "1.2.1"
)

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
// routesGenerator := InjectedRoutesGenerator


fork in run := false