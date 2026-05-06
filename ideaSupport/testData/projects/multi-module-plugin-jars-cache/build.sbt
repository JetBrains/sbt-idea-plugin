import org.jetbrains.sbtidea.Keys.*

ThisBuild / intellijBuild := "243.22562.145"

lazy val root = project.in(file("."))
  .aggregate(moduleA, moduleB, moduleC)
  .enablePlugins(SbtIdeaPlugin)

lazy val moduleA = pluginModule("moduleA")
lazy val moduleB = pluginModule("moduleB")
lazy val moduleC = pluginModule("moduleC")

def pluginModule(name: String): Project =
  Project(name, file(name))
    .enablePlugins(SbtIdeaPlugin)
    .settings(
      intellijPlugins += "org.intellij.scala".toPlugin
    )
