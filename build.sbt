name := "sbt-idea-plugin"
organization := "org.jetbrains"
sbtPlugin := true
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++=
  "org.scalaj" %% "scalaj-http" % "2.3.0" ::
    "org.pantsbuild" % "jarjar" % "1.6.6" ::
    "org.jetbrains" %% "ascii-graphs" % "0.0.6" :: Nil

crossSbtVersions := Seq("0.13.17", "1.2.6")

publishMavenStyle := false
bintrayRepository := "sbt-plugins"
bintrayOrganization := Some("jetbrains")

enablePlugins(ScriptedPlugin)
scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
scriptedBufferLog := false
