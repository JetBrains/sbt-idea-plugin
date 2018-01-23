name := "sbt-idea-plugin"
organization := "org.jetbrains"
sbtPlugin := true
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq("-deprecation", "-feature")

crossSbtVersions := Seq("1.0.4")

publishMavenStyle := false
bintrayRepository := "sbt-plugins"
bintrayOrganization := Some("jetbrains")

scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
scriptedBufferLog := false

libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.3.0"
