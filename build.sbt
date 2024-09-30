import xerial.sbt.Sonatype.GitHubHosting

Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))

// Some tests test global properties and fail when tests are run in parallel
Test / parallelExecution := false

lazy val commonSettings: Seq[Setting[?]] = Seq(
  organization          := "org.jetbrains",
  licenses              += ("MIT", url("https://opensource.org/licenses/MIT")),
  scalacOptions        ++= Seq("-deprecation", "-feature", "-release", "8", "-Xfatal-warnings"),
  javacOptions         ++= Seq("--release", "8"),
  scalaVersion := "2.12.18",
  pluginCrossBuild / sbtVersion := "1.4.5",
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  sonatypeProfileName := "org.jetbrains",
  homepage := Some(url("https://github.com/JetBrains/sbt-idea-plugin")),
  sonatypeProjectHosting := Some(GitHubHosting("JetBrains", "sbt-idea-plugin", "scala-developers@jetbrains.com")),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

lazy val core = (project in file("core"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .settings(
    name := "sbt-declarative-core"
  )

lazy val visualizer = (project in file("visualizer"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-visualizer",
    libraryDependencies += "com.github.mutcianm" %% "ascii-graphs" % "0.0.6"
  )

lazy val packaging = (project in file("packaging"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-packaging",
    libraryDependencies += "org.pantsbuild" % "jarjar" % "1.7.2"
  )

lazy val ideaSupport = (project in file("ideaSupport"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core, packaging, visualizer)
  .settings(
    name := "sbt-idea-plugin",
    libraryDependencies ++= Seq(
      "org.apache.httpcomponents.client5" % "httpclient5" % "5.3.1",

      "org.jetbrains" % "marketplace-zip-signer" % "0.1.24",
      "io.spray" %% "spray-json" % "1.3.6",
      "org.rauschig" % "jarchivelib" % "1.2.0",
      "org.ow2.asm" % "asm" % "9.6",
      "io.get-coursier" %% "coursier" % "2.1.10",

      //for file utils in tests (create/delete cerucsively/write string)
      "commons-io" % "commons-io" % "2.15.1" % Test
    )
  )

lazy val sbtIdeaPlugin = (project in file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(core, packaging, ideaSupport, visualizer)