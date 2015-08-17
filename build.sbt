lazy val commonSettings = Seq(
  organization := "org.openbci",
  version := "0.1.0",
  scalaVersion := "2.11.7"
)

/* SBT will use Maven to download JSSC */
// val RxTx = "gnu.io" % "MFizz RxTx" % "2.2-20081207"
val jssc = "org.scream3r" % "jssc" % "2.8.0"
// Consider jSSC

lazy val root = (project in file(".")).settings(
  commonSettings: _*).settings(
    name := "OpenBCIScala",
    libraryDependencies += jssc
  )
