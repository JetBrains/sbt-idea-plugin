package sbt.jetbrains.ideaPlugin

object apiAdapter {
  type CompileResult = Any
  val Using = sbt.io.Using
  type BuildDependencies = sbt.internal.BuildDependencies

  def projectJarName(project: sbt.Project): String = s"${project.id}.jar"
  def extractAffectedFiles(result: CompileResult): Seq[sbt.File] = Seq.empty
}
