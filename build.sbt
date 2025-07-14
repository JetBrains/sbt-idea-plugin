import sbt.Def
import sbt.Keys.localStaging
import sbt.internal.sona
import sbt.librarymanagement.ivy.Credentials

Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 1))

// Some tests test global properties and fail when tests are run in parallel
Test / parallelExecution := false

val MinimumSbtVersion = "1.4.5"
// This version should be backward compatible with MinimumSbtVersion
val SbtVersionForTests = "1.10.7"

ThisBuild / organization := "org.jetbrains.scala"

// Optional but nice-to-have
ThisBuild / organizationName     := "JetBrains"
ThisBuild / organizationHomepage := Some(url("https://www.jetbrains.com/"))

ThisBuild / licenses ++= Seq(
  ("MIT", url("https://opensource.org/licenses/MIT")),
  ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))
)

ThisBuild / homepage := Some(url("https://github.com/JetBrains/sbt-idea-plugin"))

// Source-control coordinates
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/JetBrains/sbt-idea-plugin"),
    "git@github.com:JetBrains/sbt-idea-plugin.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "JetBrains",
    name  = "JetBrains",
    email = "scala-developers@jetbrains.com",
    url   = url("https://github.com/JetBrains")
  )
)

val SonatypeRepoName = "Sonatype Nexus Repository Manager"

lazy val CommonSonatypeSettings: Seq[Def.Setting[?]] = Seq(
  // new setting for the Central Portal
  publishTo := {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
    else localStaging.value
  },

  // Overwrite/filter-out existing credentials
  // Use copy of `sbt.internal.SysProp.sonatypeCredentalsEnv` but with custom environment variables
  credentials := credentials.value.filter {
    case c: DirectCredentials => c.realm != SonatypeRepoName
    case _ => true
  } ++ {
    val env = sys.env.get(_)
    val newCredentials = for {
      username <- env("SONATYPE_USERNAME_NEW").map(_.trim).filter(_.nonEmpty)
      password <- env("SONATYPE_PASSWORD_NEW").map(_.trim).filter(_.nonEmpty)
    } yield Credentials(
      SonatypeRepoName,
      sona.Sona.host,
      username,
      password
    )
    if (newCredentials.isEmpty) {
      println("WARNING: no sonatype credentials found")
    }
    newCredentials
  },
)

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
    // Use latest version of sbt in tests to test against latest versions of projects using this plugin
    "org.scala-sbt" % "sbt" % SbtVersionForTests % Test,
  ),

  // Please specify explicitely which modules should be published
  publish / skip := true
)

lazy val core = (project in file("core"))
  .enablePlugins(SbtPlugin)
  .settings(CommonSettings)
  .settings(CommonSonatypeSettings)
  .dependsOn(testUtils % "test->test")
  .settings(
    name := "sbt-declarative-core",
    publish / skip := false,
  )

lazy val visualizer = (project in file("visualizer"))
  .enablePlugins(SbtPlugin)
  .settings(CommonSettings)
  .settings(CommonSonatypeSettings)
  .dependsOn(core)
  .settings(
    name := "sbt-declarative-visualizer",
    publish / skip := false,
    libraryDependencies += "com.github.mutcianm" %% "ascii-graphs" % "0.0.6",
  )

val circeVersion = "0.14.10"

lazy val packaging = (project in file("packaging"))
  .enablePlugins(SbtPlugin)
  .settings(CommonSettings)
  .settings(CommonSonatypeSettings)
  .dependsOn(core, testUtils % "test->test")
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
  .settings(CommonSettings)
  .settings(CommonSonatypeSettings)
  .dependsOn(core, packaging, visualizer, testUtils % "test->test")
  .settings(
    name := "sbt-idea-plugin",
    publish / skip := false,
    libraryDependencies ++= Seq(
      "org.apache.httpcomponents.client5" % "httpclient5" % "5.3.1",

      "org.jetbrains" % "marketplace-zip-signer" % "0.1.24",
      "io.spray" %% "spray-json" % "1.3.6",
      "org.rauschig" % "jarchivelib" % "1.2.0",
      "org.ow2.asm" % "asm" % "9.6",
      "io.get-coursier" %% "coursier" % "2.1.10",
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

lazy val sbtIdeaPlugin = (project in file("."))
  .settings(CommonSettings)
  .settings(
    ideExcludedDirectories := Seq(
      file("tempProjects"),
      file("tempIntellijSdks"),
      file("tempIntellijArtifactsDownloads"),
    )
  )
  .aggregate(core, packaging, ideaSupport, visualizer, testUtils)
