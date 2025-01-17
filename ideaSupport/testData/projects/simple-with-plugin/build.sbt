import org.jetbrains.sbtidea.Keys.*

ThisBuild / intellijBuild := "243.22562.145"

lazy val root = project.in(file("."))
  .settings(
    intellijPlugins += "org.intellij.scala".toPlugin
  )
  .enablePlugins(SbtIdeaPlugin)



