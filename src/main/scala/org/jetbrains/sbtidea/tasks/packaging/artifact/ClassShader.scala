package org.jetbrains.sbtidea.tasks.packaging.artifact

import java.nio.file.{Files, Path, StandardOpenOption}

import org.jetbrains.sbtidea.tasks.packaging.ShadePattern
import org.pantsbuild.jarjar._
import org.pantsbuild.jarjar.util.EntryStruct
import sbt.Keys.TaskStreams

class ClassShader(patterns: Seq[ShadePattern])(implicit val streams: TaskStreams) {

  private val processor = new JJProcessor(patterns.map {
    case ShadePattern(pat, res) =>
      val jRule = new Rule()
      jRule.setPattern(pat)
      jRule.setResult(res)
      jRule
  })

  private val entry = new EntryStruct

  if (streams!=null)
    streams.log.info(s"Initialized shader with ${patterns.size} patterns")

  def applyShading(from: Path, to: Path)(cont: => Unit): Unit = {
    entry.data = Files.readAllBytes(from)
    entry.name = from.toString.substring(1).replace('\\', '/') // leading '/' cannot be used in ZFS also fix class names produced under windows
    entry.time = -1
    if (processor.process(entry)) {
      val newPath = to.getFileSystem.getPath(entry.name)
      val parent = newPath.getParent
      if (parent != null && !Files.exists(parent))
        Files.createDirectories(parent)
      Files.write(newPath, entry.data, StandardOpenOption.CREATE)
    }
  }

}

class NoOpClassShader() extends ClassShader(Seq())(null) {
  override def applyShading(from: Path, to: Path)(cont: => Unit): Unit = cont
}
