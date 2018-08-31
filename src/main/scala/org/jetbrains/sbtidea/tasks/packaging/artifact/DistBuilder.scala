package org.jetbrains.sbtidea.tasks.packaging.artifact

import java.nio.file._

import org.jetbrains.sbtidea.tasks.packaging._
import sbt.Keys.TaskStreams
import sbt._

class DistBuilder(stream: TaskStreams, private val target: File) extends ArtifactBuilder[Mappings,File] {

  protected implicit val streams: TaskStreams = stream

  protected val incrementalCache = new PersistentIncrementalCache(target.toPath)

  protected def createPackager(dest: Path, shader: ClassShader) = new SimplePackager(dest, shader, incrementalCache)

  protected def createShader(patterns: Seq[ShadePattern]): ClassShader = {
    if (patterns.nonEmpty)
      new ClassShader(patterns)
    else
      new NoOpClassShader
  }

  private def processMappings(incremental: Seq[(sbt.File, Seq[Mapping])]): Unit = {
    incremental.foreach {
      case (to, Seq(mapping@Mapping(from, _, _))) if to.name.endsWith("jar") && from.name.endsWith("jar") =>
        copySingleJar(mapping)
      case (to, mappings) if to.name.endsWith("jar") =>
        packageJar(to.toPath, mappings)
      case (to, mappings) if to.toString.contains("jar!") =>
        patch(to.toPath, mappings)
      case (_, mapping) =>
        copyDir(mapping)
      case other =>
        stream.log.warn(s"wtf: $other")
    }
  }

  protected def preFilterMappings(mappings: Mappings): Mappings = {
    mappings.filter(f =>
      !f.from.toString.endsWith("jar") ||
      !f.to.exists() ||
      incrementalCache.fileChanged(f.from.toPath)
    )
  }

  protected def copySingleJar(mapping: Mapping): Unit = {
    timed(s"copyJar: ${mapping.to}", {
      val shader = createShader(mapping.metaData.shading)
      val packager = createPackager(mapping.to.toPath, shader)
      packager.copySingleJar(mapping.from.toPath)
    })
  }

  protected def copyDir(mappings: Mappings): Unit = {
    mappings.foreach {
      case Mapping(from, to1, _) if from.isDirectory => timed(s"copyDir: $to1", IO.copyDirectory(from, to1))
      case Mapping(from, to1, _)                     => timed(s"copyFile: $to1", IO.copy(Seq(from -> to1)))
    }
  }

  protected def packageJar(to: Path, mappings: Mappings): Unit = {
    if (!Files.exists(to.getParent))
      Files.createDirectories(to.getParent)
    timed(s"packageJar(${mappings.size}): $to", {
      val rules     = mappings.flatMap(_.metaData.shading).distinct
      val shader    = createShader(rules)
      val packager  = createPackager(to, shader)
      packager.mergeIntoOne(mappings.map(_.from.toPath))
    })
  }

  protected def patch(to: Path, mappings: Mappings): Unit = {
    timed(s"patch(${mappings.size}): $to", {
      val shader    = createShader(Seq.empty)
      val packager  = createPackager(to, shader)
      packager.mergeIntoOne(mappings.map(_.from.toPath))
    })
  }

  protected def filterGroupedMappings(grouped: Seq[(sbt.File, Seq[Mapping])]): Seq[(sbt.File, Seq[Mapping])] = grouped

  protected def transformMappings(structure: Mappings): Seq[(sbt.File, Seq[Mapping])] = {
    val filtered            = preFilterMappings(structure)
    val (overrides, normal) = filtered.partition(_.to.toString.contains("jar!"))
    val groupedNormal       = normal.groupBy(_.to)
    val groupedOverrides    = overrides.groupBy(_.to)
    filterGroupedMappings(groupedNormal.toSeq) ++ filterGroupedMappings(groupedOverrides.toSeq)
  }

  def packageArtifact(structure: Mappings): Unit = {
    val mappings = transformMappings(structure)
    processMappings(mappings)
    incrementalCache.close()
  }

  override def produceArtifact(structure: Mappings): sbt.File = {
    packageArtifact(structure)
    new File(".")
  }
}