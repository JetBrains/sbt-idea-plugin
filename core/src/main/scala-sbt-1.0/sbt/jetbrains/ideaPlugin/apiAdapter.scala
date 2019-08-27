package sbt.jetbrains.ideaPlugin

import java.util.Optional

object apiAdapter {
  type CompileResult = Any
  val Using = sbt.io.Using
  type BuildDependencies = sbt.internal.BuildDependencies

  def projectJarName(project: sbt.Project): String = s"${project.id}.jar"
  def extractAffectedFiles(result: CompileResult): Seq[sbt.File] = Seq.empty

  object SbtCompilationBackCompat {
    type Analysis         = sbt.internal.inc.Analysis
    type Relations        = sbt.internal.inc.Relations
    type CompileResult    = xsbti.compile.CompileResult
    type CompileAnalysis  = xsbti.compile.CompileAnalysis
    type PreviousResult   = xsbti.compile.PreviousResult
    type ClassFileManager = xsbti.compile.ClassFileManager
    type IncOptions       = xsbti.compile.IncOptions

    val Analysis = sbt.internal.inc.Analysis

    implicit class CompileResultExt(val result: PreviousResult) extends AnyVal {
      def getAnalysis: Optional[CompileAnalysis] = result.analysis()
    }

    implicit class IncOptionsExt(val options: IncOptions) extends AnyVal {
      def withClassfileManager(manager: ClassFileManager): IncOptions =
        options.withExternalHooks(options.externalHooks().withExternalClassFileManager(manager))
    }

    object PreviousResult {
      def empty(): PreviousResult =
        xsbti.compile.PreviousResult.create(Optional.empty(), Optional.empty())
    }
  }
}
