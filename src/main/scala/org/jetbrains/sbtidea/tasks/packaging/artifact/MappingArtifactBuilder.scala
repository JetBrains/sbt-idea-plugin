package org.jetbrains.sbtidea.tasks.packaging.artifact

import java.nio.file.Path

import org.jetbrains.sbtidea.tasks.packaging._
import sbt._

abstract class MappingArtifactBuilder[T] extends ArtifactBuilder [Mappings, T] {

  protected def copySingleJar(mapping: Mapping): Unit

  protected def copyDir(mappings: Mappings): Unit

  protected def packageJar(to: Path, mappings: Mappings): Unit

  protected def patch(to: Path, mappings: Mappings): Unit

  protected def unknown(mappings: Mappings): Unit

  protected def createResult: T

  protected def mappingFilter(m: Mapping): Boolean = !m.from.toString.endsWith("jar") || !m.to.exists()

  private def preFilterMappings(mappings: Mappings): Mappings = mappings.filter(mappingFilter)

  protected def processMappings(incremental: Seq[(sbt.File, Seq[Mapping])]): Unit = {
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
        unknown(other._2)
    }
  }

  protected def transformMappings(structure: Mappings): Seq[(sbt.File, Seq[Mapping])] = {
    val filtered            = preFilterMappings(structure)
    val (overrides, normal) = filtered.partition(_.to.toString.contains("jar!"))
    val groupedNormal       = normal.groupBy(_.to)
    val groupedOverrides    = overrides.groupBy(_.to)
    groupedNormal.toSeq ++ groupedOverrides.toSeq
  }

  override def produceArtifact(structure: Mappings): T = {
    val transformed = transformMappings(structure)
    processMappings(transformed)
    createResult
  }

}
