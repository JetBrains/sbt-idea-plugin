lazy val commonSettings = Seq(
  sbtPlugin             := true,
  organization          := "org.jetbrains",
  licenses              += ("MIT", url("https://opensource.org/licenses/MIT")),
  scalacOptions        ++= Seq("-deprecation", "-feature"),
  crossSbtVersions      := Seq("0.13.17", "1.2.6"),
  publishMavenStyle     := false,
  bintrayRepository     := "sbt-plugins",
  bintrayOrganization   := Some("jetbrains"),
  scriptedLaunchOpts   ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
  scriptedBufferLog     := false
)

lazy val core = (project in file("."))
  .enablePlugins(ScriptedPlugin)
  .settings(commonSettings)
  .settings(
    name := "sbt-declarative-core"
  )

lazy val visualizer = (project in file(".") / "visualizer")
  .enablePlugins(ScriptedPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-visualizer",
    libraryDependencies += "org.jetbrains" %% "ascii-graphs" % "0.0.6"
  )

lazy val packaging = (project in file(".") / "packaging")
  .enablePlugins(ScriptedPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-packaging",
    libraryDependencies += "org.pantsbuild" % "jarjar" % "1.6.6"
  )

lazy val ideaSupport = (project in file(".") / "ideaSupport")
  .enablePlugins(ScriptedPlugin)
  .settings(commonSettings)
  .dependsOn(core, packaging, visualizer)
  .settings(
    name := "sbt-idea-plugin",
    libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.3.0"
  )