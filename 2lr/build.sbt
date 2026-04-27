ThisBuild / scalaVersion := "3.3.3"
ThisBuild / version      := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "postoffice-tagless-final",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"   % "2.10.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",
    ),
    javacOptions ++= Seq("-encoding", "UTF-8"),
    scalacOptions ++= Seq("-encoding", "UTF-8"),
  )
