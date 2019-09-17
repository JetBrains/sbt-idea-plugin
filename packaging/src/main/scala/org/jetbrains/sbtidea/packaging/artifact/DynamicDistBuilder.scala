package org.jetbrains.sbtidea.packaging.artifact

import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import org.jetbrains.sbtidea.packaging.PackagingKeys.ExcludeFilter
import org.jetbrains.sbtidea.packaging._
import sbt.File
import sbt.Keys.TaskStreams

class DynamicDistBuilder(stream: TaskStreams, target: File, outputDir: File, private val hints: Seq[File]) extends DistBuilder(stream, target) {

  override def packageJar(to: Path, mappings: Mappings): Unit = {
    val isStatic = mappings.forall(_.metaData.static)
    if (isStatic)
      super.packageJar(to, mappings)
    else {
      val newOutputPath = outputDir.toPath.resolve("classes")
      if (!Files.exists(newOutputPath) || hints.isEmpty)
        packageNoHints(newOutputPath, mappings)
      else
        packageUsingHints(newOutputPath)
    }
  }

  private def packageUsingHints(newOutputPath: Path): Unit = {
    timed(s"Using ${hints.size} hints from previous compilation: $newOutputPath", {
      val key = "classes"
      val offset = key.length + 1
      for (hint <- hints) {
        val hintStr = hint.toString
        val relativisedStr = hintStr.substring(hintStr.indexOf(key) + offset)
        val newRelativePath = Paths.get(relativisedStr)
        val newAbsolutePath = newOutputPath.resolve(newRelativePath)
        if (newAbsolutePath.toFile.getParentFile == null || !newAbsolutePath.toFile.getParentFile.exists())
          Files.createDirectories(newAbsolutePath.getParent)
        Files.copy(hint.toPath, newAbsolutePath, StandardCopyOption.REPLACE_EXISTING)
      }
    })
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
