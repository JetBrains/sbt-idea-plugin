package sbt.jetbrains.ideaPlugin

object apiAdapter {
  val Using = sbt.Using
  def projectJarName(project: sbt.Project): String = s"${project.id}.jar"
}
