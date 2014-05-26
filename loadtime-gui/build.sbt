name := "loadtime-gui"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "junit" % "junit" % "4.11",
  "commons-io" % "commons-io" % "2.4",
  "com.google.guava" % "guava" % "15.0-rc1",
   "org.mongodb" % "mongo-java-driver" % "2.11.3"
)     

play.Project.playJavaSettings
