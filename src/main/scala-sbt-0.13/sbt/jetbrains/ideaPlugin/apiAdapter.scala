package sbt.jetbrains.ideaPlugin

import sbt.File
import sbt.inc._

import java.util.Optional

object apiAdapter {
  type CompileResult = sbt.inc.Analysis
  type BuildDependencies = sbt.BuildDependencies
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

  object SbtCompilationBackCompat {
    type Analysis         = sbt.inc.Analysis
    type Relations        = sbt.inc.Relations
    type CompileResult    = sbt.Compiler.CompileResult
    type CompileAnalysis  = sbt.inc.Analysis
    type PreviousResult   = sbt.Compiler.PreviousAnalysis
    type ClassFileManager = sbt.inc.ClassfileManager
    type IncOptions       = sbt.inc.IncOptions

    val Analysis = sbt.inc.Analysis

    implicit class CompileResultExt(val result: PreviousResult) extends AnyVal {
      def getAnalysis: Optional[CompileAnalysis] = Optional.of(result.analysis)
    }

    implicit class IncOptionsExt(val options: IncOptions) extends AnyVal {
      def withClassfileManager(manager: ClassFileManager): IncOptions =
        options.withNewClassfileManager(() => manager)
    }

    object PreviousResult {
      def empty(): PreviousResult =
        sbt.Compiler.PreviousAnalysis(Analysis.Empty, None)
    }
  }

}
