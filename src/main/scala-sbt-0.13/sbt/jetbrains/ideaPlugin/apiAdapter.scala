package sbt.jetbrains.ideaPlugin

import sbt.File
import sbt.inc._

object apiAdapter {
  type CompileResult = sbt.inc.Analysis
  val Using = sbt.Using
  def projectJarName(project: sbt.Project): String = s"${project.id}.jar"
  def extractAffectedFiles(initialTimestamp: Long, result: Seq[CompileResult]): Seq[File] = {
    def processCompilation(compileResult: CompileResult): Seq[File] = {
      val lastCompilation = compileResult.compilations.allCompilations.find(_.startTime() >= initialTimestamp).getOrElse(return Seq.empty)
      val startTime       = lastCompilation.startTime()
      val res = compileResult.stamps.products.collect {
        case (f, s:LastModified) if s.value >= startTime => f
      }.toSeq
      res
    }
    val res = result.flatMap(processCompilation)
    res
  }
}
