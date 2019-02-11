package org.jetbrains.sbtidea.tasks

import java.net.{HttpURLConnection, URL, URLConnection}
import java.nio.file.{FileSystems, Path}

import org.jetbrains.sbtidea.Keys.PackagingMethod
import org.jetbrains.sbtidea.tasks.packaging.ExcludeFilter.ExcludeFilter
import sbt.Def.Classpath
import sbt.Keys.TaskStreams
import sbt._

import scala.util.control.NonFatal

package object packaging {

  case class ProjectData(thisProject: ProjectRef,
                         cp: Classpath,
                         definedDeps: Seq[ModuleID],
                         additionalProjects: Seq[Project],
                         assembleLibraries: Boolean,
                         productDirs: Seq[File],
                         report: UpdateReport,
                         libMapping: Seq[(ModuleID, Option[String])],
                         additionalMappings: Seq[(File, String)],
                         packageMethod: PackagingMethod,
                         shadePatterns: Seq[ShadePattern],
                         excludeFilter: ExcludeFilter
                        )


  private[packaging] object MAPPING_KIND extends Enumeration {
    type MAPPING_KIND = Value
    val TARGET, LIB, LIB_ASSEMBLY, MISC, UNDEFINED = Value
  }

  private[packaging] case class MappingMetaData(shading: Seq[ShadePattern], excludeFilter: ExcludeFilter, static: Boolean, project: Option[String], kind: MAPPING_KIND.MAPPING_KIND)
  private[packaging] object     MappingMetaData { val EMPTY = MappingMetaData(Seq.empty, ExcludeFilter.AllPass, static = true, project = None, kind = MAPPING_KIND.UNDEFINED) }

  private[packaging] case class Mapping(from: File, to: File, metaData: MappingMetaData)

  type Mappings = Seq[Mapping]

  class SkipEntryException extends Exception

  implicit def MappingOrder[A <: Mapping]: Ordering[A] = Ordering.by(x => x.from -> x.to) // order by target jar file

  implicit class ModuleIdExt(val moduleId: ModuleID) extends AnyVal {

    def key(implicit scalaVersion: ProjectScalaVersion): ModuleKey = {
      val versionSuffix = moduleId.crossVersion match {
        case _:CrossVersion.Binary if scalaVersion.isDefined =>
          "_" + CrossVersion.binaryScalaVersion(scalaVersion.str)
        case _ => ""
      }

      ModuleKey(
        moduleId.organization % (moduleId.name + versionSuffix) % moduleId.revision,
        moduleId.extraAttributes
          .map    { case (k, v) => k.stripPrefix("e:") -> v }
          .filter { case (k, _) => k == "scalaVersion" || k == "sbtVersion" })
    }

  }

  def timed[T](msg: String, f: => T)(implicit streams: TaskStreams): T = {
    val start = System.currentTimeMillis()
    val res = f
    streams.log.info(s"(${System.currentTimeMillis() - start}ms) $msg")
    res
  }

  def withConnection[V](url: URL)(f: => HttpURLConnection => V): V = {
    var connection: HttpURLConnection = null
    try {
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      f(connection)
    } finally {
      try {
        if (connection != null) connection.disconnect()
      } catch {
        case e: Exception =>
          println(s"Failed to close connection $url: ${e.getMessage}")
      }
    }
  }

  def using[T <: AutoCloseable, V](r: => T)(f: T => V): V = {
    val resource: T = r
    require(resource != null, "resource is null")
    var exception: Throwable = null
    try {
      f(resource)
    } catch {
      case NonFatal(e) =>
        exception = e
        throw e
    } finally {
      if (resource != FileSystems.getDefault)
        closeAndAddSuppressed(exception, resource)
    }
  }

  private def closeAndAddSuppressed(e: Throwable,
                                    resource: AutoCloseable): Unit = {
    if (e != null) {
      try {
        resource.close()
      } catch {
        case NonFatal(suppressed) =>
          e.addSuppressed(suppressed)
      }
    } else {
      resource.close()
    }
  }
}
