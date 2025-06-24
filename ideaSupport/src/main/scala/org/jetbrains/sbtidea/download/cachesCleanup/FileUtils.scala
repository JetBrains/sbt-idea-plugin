package org.jetbrains.sbtidea.download.cachesCleanup

import org.apache.commons.io

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path}
import java.time.{Instant, LocalDate, ZoneId}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*
import scala.util.Using

private object FileUtils {

  /**
   * Safely collects file information from a directory (non-recursive)
   */
  def collectFiles(directory: Path, filter: Path => Boolean = _ => true): Seq[FileMetaInfo] = {
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
      return Seq.empty
    }

    Using.resource(Files.list(directory)) { stream =>
      stream
        .collect(Collectors.toList[Path])
        .asScala
        .filter(filter)
        .flatMap(collectFileInfo)
    }
  }

  /**
   * Safely extracts file metadata
   */
  def collectFileInfo(path: Path): Option[FileMetaInfo] = {
    try {
      val attrs = Files.readAttributes(path, classOf[BasicFileAttributes])
      val metaData = DirectoryMetaData(getCreationDate(attrs))
      Some(FileMetaInfo(path, metaData))
    } catch {
      case _: Exception => None
    }
  }

  private def getCreationDate(attrs: BasicFileAttributes): LocalDate = {
    val creationTime = attrs.creationTime()
    Instant.ofEpochMilli(creationTime.toMillis)
      .atZone(ZoneId.systemDefault())
      .toLocalDate
  }

  /**
   * Gets file size safely
   */
  def getFileSize(file: Path): Long = {
    try {
      Files.size(file)
    } catch {
      case _: Exception => 0L
    }
  }

  /**
   * Gets directory size safely using Apache Commons FileUtils
   */
  def getDirectorySize(directory: Path): Long = {
    try {
      io.FileUtils.sizeOfDirectory(directory.toFile)
    } catch {
      case _: java.io.FileNotFoundException => 0L // Handle "mock" directories that don't exist
      case _: java.io.UncheckedIOException => 0L // Handle "mock" directories that don't exist
      case _: Exception => 0L // Handle any other exceptions
    }
  }

  /**
   * Safely deletes a directory using Apache Commons FileUtils
   */
  def deleteDirectory(directory: Path): Unit = {
    io.FileUtils.deleteDirectory(directory.toFile)
  }
}