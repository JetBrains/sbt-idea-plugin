lazy val commonSettings: Seq[Def.Setting[_]] = Seq(
  sbtPlugin             := true,
  organization          := "org.jetbrains",
  licenses              += ("MIT", url("https://opensource.org/licenses/MIT")),
  scalacOptions        ++= Seq("-deprecation", "-feature"),
  crossSbtVersions      := Seq("0.13.17", "1.2.6"),
  publishMavenStyle     := false,
  bintrayRepository     := "sbt-plugins",
  bintrayOrganization   := Some("jetbrains"),
  sources in (Compile,doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false
)

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "sbt-declarative-core"
  )

lazy val visualizer = (project in file(".") / "visualizer")
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-visualizer",
    libraryDependencies += "org.jetbrains" %% "ascii-graphs" % "0.0.6"
  )

lazy val packaging = (project in file(".") / "packaging")
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-packaging",
    libraryDependencies += "org.pantsbuild" % "jarjar" % "1.6.6"
  )

lazy val ideaSupport = (project in file(".") / "ideaSupport")
  .settings(commonSettings)
  .dependsOn(core, packaging, visualizer)
  .settings(
    name := "sbt-idea-plugin",
    libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.3.0"
  )

lazy val root = (project in file("."))
  .aggregate(core, packaging, ideaSupport, visualizer)