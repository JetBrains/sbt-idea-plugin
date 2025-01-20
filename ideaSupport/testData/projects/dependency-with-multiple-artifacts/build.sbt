//
// EXAMPLE ADAPTED FROM:
// https://github.com/JetBrains/sbt-idea-plugin/issues/135
//
import org.jetbrains.sbtidea.Keys.*

lazy val myAwesomeFramework =
  project.in(file("."))
    .enablePlugins(SbtIdeaPlugin)
    .settings(
      scalaVersion := "2.13.15",
      ThisBuild / intellijPluginName := "My Awesome Framework",
      ThisBuild / intellijBuild := "243.22562.145",
      ThisBuild / intellijPlatform := IntelliJPlatform.IdeaCommunity,
      libraryDependencies ++= Seq(
        "org.lwjglx" % "lwjgl3-awt" % "0.2.3",
        "org.lwjgl" % "lwjgl" % "3.3.6",
        "org.lwjgl" % "lwjgl-opengl" % "3.3.6",
        ("org.lwjgl" % "lwjgl" % "3.3.6" % Runtime)
          .classifier("natives-windows")
          .classifier("natives-windows-x86")
          .classifier("natives-linux")
          .classifier("natives-macos")
          .classifier("natives-macos-arm64"),
        ("org.lwjgl" % "lwjgl-vulkan" % "3.3.6" % Runtime)
          .classifier("natives-macos")
          .classifier("natives-macos-arm64")
      ),
    )
