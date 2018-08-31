package org.jetbrains.sbtidea.tasks.packaging.artifact

import java.nio.file.Path

import org.jetbrains.sbtidea.tasks.packaging._
import sbt.File
import sbt.Keys.TaskStreams

class StaticDistBuilder(stream: TaskStreams, target: File, outputDir: File) extends DistBuilder(stream, target) {

  override protected def packageJar(to: Path, mappings: Mappings): Unit = {
    val isStatic = mappings.forall(_.metaData.static)
    if (isStatic)
      super.packageJar(to, mappings)
    else {
      val newOutputPath = outputDir.toPath.resolve("classes")
      val packager = new StaticPackager(newOutputPath, new NoOpClassShader, incrementalCache)
      timed(s"classes(${mappings.size}): $newOutputPath",
        packager.mergeIntoOne(mappings.map(_.from.toPath))
      )
    }
  }

  override protected def patch(to: Path, mappings: Mappings): Unit = {
    streams.log.info(s"Patching has no effect when building dynamic artifact")
  }

}
