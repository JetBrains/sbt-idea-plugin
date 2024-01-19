package org.jetbrains.sbtidea.packaging.artifact

import org.jetbrains.sbtidea.packaging.*
import sbt.File
import sbt.Keys.TaskStreams

import java.nio.file.Path

class DynamicDistBuilder(stream: TaskStreams, target: File, outputDir: File) extends DistBuilder(stream, target) {

  override def packageJar(to: Path, mappings: Mappings): Unit = {
    val isStatic = mappings.forall(_.metaData.static)
    if (isStatic)
      super.packageJar(to, mappings)
    else {
      val newOutputPath = outputDir.toPath.resolve("classes")
      packageNoHints(newOutputPath, mappings)
    }
  }

  private def packageNoHints(newOutputPath: Path, mappings: Mappings): Unit = {
    val packager = new DynamicPackager(newOutputPath, new NoOpClassShader, ExcludeFilter.AllPass, incrementalCache)
    timed(s"classes(${mappings.size}): $newOutputPath",
      packager.mergeIntoOne(mappings.map(_.from.toPath))
    )
  }

  override def patch(to: Path, mappings: Mappings): Unit = {
    streams.log.info(s"Patching has no effect when building dynamic artifact")
  }
}
