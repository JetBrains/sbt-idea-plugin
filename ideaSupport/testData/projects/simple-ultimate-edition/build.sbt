import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.IntelliJPlatform

ThisBuild / intellijBuild := "243.22562.145"
ThisBuild / intellijPlatform := IntelliJPlatform.IdeaUltimate

lazy val root = project.in(file("."))
  .enablePlugins(SbtIdeaPlugin)