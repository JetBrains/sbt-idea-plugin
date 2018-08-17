package sbt.jetbrains.ideaPlugin

object apiAdapter {
  val Using = sbt.io.Using
  type BuildDependencies = sbt.internal.BuildDependencies

  def projectJarName(project: sbt.Project): String = s"${project.id}.jar"
}
