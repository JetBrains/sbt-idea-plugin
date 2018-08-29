package org.jetbrains.sbtidea.tasks.packaging

import java.nio.file._

import sbt.Keys.TaskStreams
import sbt._

trait ArtifactBuilder[T, U] {
    def produceArtifact(structure: T): U
}

class DistBuilder(stream: TaskStreams, private val target: File) extends ArtifactBuilder[Mappings,File] {

  private implicit val streams: TaskStreams = stream

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
      case (to, Seq(Mapping(from, _, metaData))) if to.name.endsWith("jar") && from.name.endsWith("jar") =>
        timed(s"copyJar: $to", {
          val shader = createShader(metaData.shading)
          val packager = createPackager(to.toPath, shader)
          packager.copySingleJar(from.toPath)
        })
      case (to, mappings) if to.name.endsWith("jar") =>
        if (!to.getParentFile.exists())
          to.getParentFile.mkdirs()
        timed(s"packageJar(${mappings.size}): $to", {
          val rules     = mappings.flatMap(_.metaData.shading).distinct
          val shader    = createShader(rules)
          val packager  = createPackager(to.toPath, shader)
          packager.mergeIntoOne(mappings.map(_.from.toPath))
        })
      case (to, mappings) if to.toString.contains("jar!") =>
        timed(s"patch(${mappings.size}): $to", {
          val shader    = createShader(Seq.empty)
          val packager  = createPackager(to.toPath, shader)
          packager.mergeIntoOne(mappings.map(_.from.toPath))
        })
      case (to, mapping) =>
        timed(s"copyDir: $to", {
          mapping.foreach {
            case Mapping(from, to1, _) if from.isDirectory => IO.copyDirectory(from, to1)
            case Mapping(from, to1, _) => IO.copy(Seq(from -> to1))
          }
        })
      case other => stream.log.warn(s"wtf: $other")
    }
  }

  private def filterUnchangedJars(mappings: Mappings): Mappings = {
    mappings.filter(f =>
      !f.from.toString.endsWith("jar") ||
      !f.to.exists() ||
      incrementalCache.fileChanged(f.from.toPath)
    )
  }

  protected def transformMappings(structure: Mappings): Seq[(sbt.File, Seq[Mapping])] = {
    val filtered = filterUnchangedJars(structure)
    val (overrides, normal) = filtered.partition(_.to.toString.contains("jar!"))
    val groupedNormal       = normal.groupBy(_.to)
    val groupedOverrides    = overrides.groupBy(_.to)
    groupedNormal.toSeq ++ groupedOverrides.toSeq
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

class ZipDistBuilder(private val dest: File)(implicit stream: TaskStreams) extends ArtifactBuilder[File, File] {
  override def produceArtifact(source: File): sbt.File = {
    val packager = new ZipPackager(dest.toPath)
    timed(s"Packaging ZIP artifact: $dest", {
      packager.mergeIntoOne(Seq(source.toPath))
    })
    dest
  }
}

//class StaticDistBuilder(private val streams: TaskStreams) extends DistBuilder(streams) {
//  override def produceArtifact(structure: Mappings): sbt.File = {
//    ???
//  }
//}

