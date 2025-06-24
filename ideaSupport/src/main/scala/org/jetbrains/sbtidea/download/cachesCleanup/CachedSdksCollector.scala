package org.jetbrains.sbtidea.download.cachesCleanup

import org.jetbrains.sbtidea.download.Version

import java.nio.file.{Files, Path}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.util.Using

object CachedSdksCollector {

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
        FileUtils.collectFileInfo(directory) match {
          case Some(fileInfo) =>
            Some(IntellijSdkDirInfo(directory, Version(dirName), majorVersion, fileInfo.metaData))
          case None => None
        }
      case _ => None
    }
  }
}