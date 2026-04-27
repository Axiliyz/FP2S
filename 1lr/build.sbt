val scala3Version = "3.3.4"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "post-office-monads",
    version      := "0.1.0",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    run / fork         := true,
    run / connectInput := true,
    run / javaOptions  += "-Dfile.encoding=UTF-8",
    run / outputStrategy := Some(StdoutOutput)
  )
