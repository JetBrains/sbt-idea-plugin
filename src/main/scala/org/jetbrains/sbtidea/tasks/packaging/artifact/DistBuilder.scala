package org.jetbrains.sbtidea.tasks.packaging.artifact

import java.nio.file._

import ExcludeFilter.ExcludeFilter
import org.jetbrains.sbtidea.tasks.packaging._
import sbt.Keys.TaskStreams
import sbt._

class DistBuilder(stream: TaskStreams, private val target: File) extends MappingArtifactBuilder[File] {

  protected implicit val streams: TaskStreams = stream

  protected val incrementalCache = new PersistentIncrementalCache(target.toPath)

  protected def createPackager(dest: Path, shader: ClassShader, excludeFilter: ExcludeFilter) =
    new SimplePackager(dest, shader, excludeFilter, incrementalCache)

  protected def createShader(patterns: Seq[ShadePattern]): ClassShader = {
    if (patterns.nonEmpty)
      new ClassShader(patterns)
    else
      new NoOpClassShader
  }

  override def mappingFilter(m: Mapping): Boolean = super.mappingFilter(m) || incrementalCache.fileChanged(m.from.toPath)

  override def copySingleJar(mapping: Mapping): Unit = {
    timed(s"copyJar: ${mapping.to}", {
      val shader = createShader(mapping.metaData.shading)
      val filter = mapping.metaData.excludeFilter
      val packager = createPackager(mapping.to.toPath, shader, filter)
      packager.copySingleJar(mapping.from.toPath)
    })
  }

  override def copyDirs(mappings: Mappings): Unit = {
    mappings.foreach {
      case Mapping(from, to1, _) if from.isDirectory => timed(s"copyDir: $to1", IO.copyDirectory(from, to1))
      case Mapping(from, to1, _)                     => timed(s"copyFile: $to1", IO.copy(Seq(from -> to1)))
    }
  }

  override def packageJar(to: Path, mappings: Mappings): Unit = {
    if (!Files.exists(to.getParent))
      Files.createDirectories(to.getParent)
    timed(s"packageJar(${mappings.size}): $to", {
      val rules     = mappings.flatMap(_.metaData.shading).distinct
      val shader    = createShader(rules)
      val filter    = ExcludeFilter.merge(mappings.map(_.metaData.excludeFilter))
      val packager  = createPackager(to, shader, filter)
      packager.mergeIntoOne(mappings.map(_.from.toPath))
    })
  }

  override def patch(to: Path, mappings: Mappings): Unit = {
    timed(s"patch(${mappings.size}): $to", {
      val shader    = createShader(Seq.empty)
      val filter    = (_:Path) => false
      val packager  = createPackager(to, shader, filter)
      packager.mergeIntoOne(mappings.map(_.from.toPath))
    })
  }

  override def unknown(mappings: Mappings): Unit = streams.log.warn(s"wtf: $mappings")

  override def createResult = {
    incrementalCache.close()
    target
  }

}