import xerial.sbt.Sonatype.GitHubHosting

val scala210 = "2.10.7"
val scala212 = "2.12.9"

ThisBuild / scalaVersion := scala212

Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))

lazy val commonSettings: Seq[Def.Setting[_]] = Seq(
  organization          := "org.jetbrains",
  licenses              += ("MIT", url("https://opensource.org/licenses/MIT")),
  scalacOptions        ++= Seq("-deprecation", "-feature", "-target:jvm-1.8"),
  javacOptions         ++= Seq("--release", "8"),
  crossScalaVersions    := Seq(scala212, scala210),
  // emulate sbt cross building by Scala cross building
  // since we can assume Scala 2.12.x to be sbt 1.x
  // https://github.com/sbt/sbt-pgp/pull/115
  pluginCrossBuild / sbtVersion := {
    scalaBinaryVersion.value match {
      case "2.10" => "0.13.18"
      case "2.12" => (ThisBuild / sbtVersion).value
    }
  },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  sonatypeProfileName := "org.jetbrains",
  homepage := Some(url("https://github.com/JetBrains/sbt-idea-plugin")),
  sonatypeProjectHosting := Some(GitHubHosting("JetBrains", "sbt-idea-plugin", "scala-developers@jetbrains.com")),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"
)

lazy val core = (project in file("core"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .settings(
    name := "sbt-declarative-core"
  )

lazy val visualizer = (project in file(".") / "visualizer")
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-visualizer",
    libraryDependencies += "com.github.mutcianm" %% "ascii-graphs" % "0.0.6"
  )

lazy val packaging = (project in file(".") / "packaging")
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-packaging",
    libraryDependencies += "org.pantsbuild" % "jarjar" % "1.6.6"
  )

lazy val ideaSupport = (project in file(".") / "ideaSupport")
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .dependsOn(core, packaging, visualizer)
  .settings(
    name := "sbt-idea-plugin",
    libraryDependencies ++=
        "org.scalaj" %% "scalaj-http" % "2.3.0"                       ::
          "org.jetbrains" % "marketplace-zip-signer" % "0.1.0"        ::
          "com.eclipsesource.minimal-json" % "minimal-json" % "0.9.5" ::
          "org.rauschig" % "jarchivelib" % "1.0.0"                    :: Nil
  )

lazy val sbtIdeaPlugin = (project in file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(core, packaging, ideaSupport, visualizer)
