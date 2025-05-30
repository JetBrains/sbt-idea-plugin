package org.jetbrains.sbtidea.download.sdkCleanup

import org.jetbrains.sbtidea.download.Version

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path}
import java.time.{Instant, ZoneId}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.util.Using

object CachedSdksCollector {

  /**
   * Represents a summary of IntelliJ IDEs cached on disk in SDKs root
   * (e.g., in `.ScalaPluginIC/sdk` directory)
   *
   * @param sdkInfos      The list of detected SDK
   * @param baseDirectory The base directory that was searched
   */
  case class CachedSdksReport(
    sdkInfos: Seq[IntellijSdkDirInfo],
    baseDirectory: Path,
  )

  def collectSdks(baseDirectory: Path): CachedSdksReport = {
    val directories = listDirectories(baseDirectory)
    val sdkDirInfos = directories.flatMap(parseIntellijSdkDirCandidate)
    CachedSdksReport(sdkDirInfos, baseDirectory)
  }

  private def listDirectories(baseDirectory: Path): Seq[Path] = {
    val childDirsStream = Files.list(baseDirectory).filter(Files.isDirectory(_))
    Using.resource(childDirsStream)(_.collect(Collectors.toList[Path]).asScala.toSeq)
  }

  // Examples:
  // - 251.1234
  // - 251.1234.123
  // - 251.WHATEVER
  private val IntelliJVersionPattern = """(\d{3})\..*""".r

  private def parseIntellijSdkDirCandidate(directory: Path): Option[IntellijSdkDirInfo] = {
    val dirName = directory.getFileName.toString
    dirName match {
      case IntelliJVersionPattern(majorVersionStr) =>
        val majorVersion = majorVersionStr.toInt
        val dirInfo = getDirMetaData(directory)
        Some(IntellijSdkDirInfo(directory, Version(dirName), majorVersion, dirInfo))
      case _ => None
    }
  }

  private def getDirMetaData(directory: Path): DirectoryMetaData = {
    val attrs = Files.readAttributes(directory, classOf[BasicFileAttributes])
    val creationDate = Instant.ofEpochMilli(attrs.creationTime().toMillis)
      .atZone(ZoneId.systemDefault())
      .toLocalDate
    DirectoryMetaData(creationDate)
  }
}
