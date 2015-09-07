lazy val commonSettings = Seq(
  organization := "org.openbci",
  version := "0.1.0",
  scalaVersion := "2.11.7"
)

/* SBT will use Maven to download JSSC */
val jssc = "org.scream3r" % "jssc" % "2.8.0"

lazy val root = (project in file(".")).settings(
  commonSettings: _*).settings(
    name := "OpenBCIScala",
    // Avoid UnsatisfiedLinkError with Native Libraries.
    fork in (Test, run) := true,
    fork in (Compile, run) := true,
    // Forward stdin to the forked JVM
    connectInput in run := true,
    libraryDependencies += jssc
  )
