package org.jetbrains.sbtidea.packaging.artifact

import org.jetbrains.sbtidea.packaging.*
import sbt.*
import sbt.Keys.TaskStreams

import java.nio.file.*

class DistBuilder(stream: TaskStreams, private val target: File) extends MappingArtifactBuilder[File] {

  protected implicit val streams: TaskStreams = stream

  protected lazy val incrementalCache: IncrementalCache = new PersistentIncrementalCache(target.toPath)

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
      val filter    = ExcludeFilter.AllPass
      val packager  = createPackager(to, shader, filter)
      packager.mergeIntoOne(mappings.map(_.from.toPath))
    })
  }

  override def unknown(mappings: Mappings): Unit = streams.log.warn(s"wtf: $mappings")

  override def createResult = {
    incrementalCache.close()
    target
  }

  override def produceArtifact(structure: Mappings): sbt.File =
    timed("<== Artifact total assembly ==>", {super.produceArtifact(structure)})

}