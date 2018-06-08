package sbt.jetbrains

object apiAdapter {
  val Using = sbt.Using
  def projectJarName(project: sbt.Project): String = s"${project.id}.jar"
}
