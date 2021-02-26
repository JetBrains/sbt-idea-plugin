package sbt.jetbrains.ideaPlugin

import java.io.InputStream
import java.nio.file.{Files, Path}
import java.util.Optional

object apiAdapter {
  type CompileResult = Any
  val Using = sbt.io.Using
  type BuildDependencies = sbt.internal.BuildDependencies

  def extractAffectedFiles(result: CompileResult): Seq[sbt.File] = Seq.empty

  def projectJarName(project: sbt.Project): String = s"${project.id}.jar"

  // / method is missing because it's already implemented in sbt 1.3 PathOps
  final class PathExt(val path: Path) extends AnyVal {
    import scala.collection.JavaConverters.asScalaIteratorConverter

    def list: Seq[Path] = Files.list(path).iterator().asScala.toSeq
    def exists: Boolean = Files.exists(path)
    def isDir: Boolean = Files.isDirectory(path)
    def inputStream: InputStream = Files.newInputStream(path)
  }
}
