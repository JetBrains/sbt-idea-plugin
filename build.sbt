import sbt.{Def, ThisBuild, url}

Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))

// Some tests test global properties and fail when tests are run in parallel
ThisBuild / Test / parallelExecution := false

lazy val PublishingSettings: Seq[Def.Setting[?]] = Seq(
  organization := "org.jetbrains.scala",

  // Optional but nice-to-have
  organizationName := "JetBrains",
  organizationHomepage := Some(url("https://www.jetbrains.com/")),

  licenses ++= Seq(
    ("MIT", url("https://opensource.org/licenses/MIT")),
    ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))
  ),

  homepage := Some(url("https://github.com/JetBrains/sbt-idea-plugin")),

  // Source-control coordinates
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/JetBrains/sbt-idea-plugin"),
      "git@github.com:JetBrains/sbt-idea-plugin.git"
    )
  ),

  // Required by Sonatype for publishing
  developers := List(
    Developer(
      id = "JetBrains",
      name = "JetBrains",
      email = "scala-developers@jetbrains.com",
      url = url("https://github.com/JetBrains")
    )
  ),
)

val MinimumSbtVersion = "1.4.5"
// This version should be backward compatible with MinimumSbtVersion
val SbtVersionForTests = "1.10.7"

lazy val CommonSettings: Seq[Setting[?]] = Seq(
  scalaVersion := "2.12.18",
  pluginCrossBuild / sbtVersion := MinimumSbtVersion,

  Compile / scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings"),

  // It's fine to require later JDK level.
  // You still JDK >= 17 when developing IntelliJ plugin as IntelliJ requires JDK 17 (at least in 2024.3)
  Compile / javacOptions ++= Seq("--release", "11"),
  Compile / scalacOptions ++= Seq("-release", "11"),

  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    // Use the latest version of sbt in tests to test against the latest versions of projects using this plugin
    "org.scala-sbt" % "sbt" % SbtVersionForTests % Test,
  ),

  // Please specify explicitely which modules should be published
  publish / skip := true
)

lazy val sbtIdeaPlugin = (project in file("."))
  .settings(CommonSettings)
  .settings(
    ideExcludedDirectories := Seq(
      file("target"),
      file("tempProjects"),
      file("tempIntellijSdks"),
      file("tempIntellijArtifactsDownloads"),
    )
  )
  .aggregate(core, packaging, ideaSupport, visualizer, testUtils)

lazy val core = (project in file("core"))
  .enablePlugins(SbtPlugin)
  .dependsOn(testUtils % "test->test")
  .settings(CommonSettings)
  .settings(PublishingSettings)
  .settings(
    name := "sbt-declarative-core",
    publish / skip := false,
  )

lazy val visualizer = (project in file("visualizer"))
  .enablePlugins(SbtPlugin)
  .dependsOn(core)
  .settings(CommonSettings)
  .settings(PublishingSettings)
  .settings(
    name := "sbt-declarative-visualizer",
    publish / skip := false,
    libraryDependencies += "com.github.mutcianm" %% "ascii-graphs" % "0.0.6",
  )

val circeVersion = "0.14.14"

lazy val packaging = (project in file("packaging"))
  .enablePlugins(SbtPlugin)
  .dependsOn(core, testUtils % "test->test")
  .settings(CommonSettings)
  .settings(PublishingSettings)
  .settings(
    name := "sbt-declarative-packaging",
    publish / skip := false,
    libraryDependencies ++= Seq(
      "org.pantsbuild" % "jarjar" % "1.7.2",
      "io.circe" %% "circe-core" % circeVersion % Test,
      "io.circe" %% "circe-generic" % circeVersion % Test,
      "io.circe" %% "circe-parser" % circeVersion % Test
    ),
  )

lazy val ideaSupport = (project in file("ideaSupport"))
  .enablePlugins(SbtPlugin)
  .dependsOn(core, packaging, visualizer, testUtils % "test->test")
  .settings(CommonSettings)
  .settings(PublishingSettings)
  .settings(
    name := "sbt-idea-plugin",
    publish / skip := false,
    libraryDependencies ++= Seq(
      "org.apache.httpcomponents.client5" % "httpclient5" % "5.5",

      "org.jetbrains" % "marketplace-zip-signer" % "0.1.38",
      "io.spray" %% "spray-json" % "1.3.6",
      "org.rauschig" % "jarchivelib" % "1.2.0",
      "org.ow2.asm" % "asm" % "9.8",
      "io.get-coursier" %% "coursier" % "2.1.24",
      "commons-io" % "commons-io" % "2.19.0",
    ),
  )

lazy val testUtils = (project in file("testUtils"))
  .settings(CommonSettings)
  .settings(
    name := "test-utils",
    scalacOptions ++= Seq(
      "-Xsource:3"
    )
  )