package org.jetbrains.sbtidea.download.sdkCleanup

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.Version

import java.nio.file.{Files, Path, Paths}
import java.time.LocalDate
import scala.collection.mutable.ArrayBuffer

object TestUtils {

  // Mock the current date to be 30 May 2025
  val MockedTodayDate: LocalDate = LocalDate.parse("2025-05-30")

  def createSdkInfoMock(majorVersion: Int, minorVersion: Int, daysAgo: Int): IntellijSdkDirInfo = {
    val versionString = s"$majorVersion.$minorVersion"
    val creationDate = MockedTodayDate.minusDays(daysAgo)
    val dirInfo = DirectoryMetaData(
      creationDate = creationDate
    )

    IntellijSdkDirInfo(
      directory = Paths.get(s"/mock/path/$versionString"),
      fullVersion = Version(versionString),
      majorVersion = majorVersion,
      dirInfo = dirInfo
    )
  }

  // Helper method to check if a directory exists with a proper assertion message
  def assertDirectoryExists(dir: Path, shouldExist: Boolean): Unit = {
    val exists = Files.exists(dir)
    val message = if (shouldExist) s"Directory should exist: $dir" else s"Directory should not exist: $dir"
    assert(exists == shouldExist, message)
  }

  class CapturingTestLogger extends PluginLogger {
    private val messages: ArrayBuffer[String] = ArrayBuffer.empty

    def getLoggedText: String =
      messages.mkString("\n")

    private def append(severity: String, msg: String): Unit = {
      messages ++= msg.linesIterator.map(line => s"[$severity] $line")
    }

    override def info(msg: => String): Unit = append("info", msg)

    override def warn(msg: => String): Unit = append("warn", msg)

    override def error(msg: => String): Unit = append("error", msg)

    override def fatal(msg: => String): Unit = append("fatal", msg)
  }
}
