package org.jetbrains.sbtidea.download

import java.io.File
import scala.io.Source

object IntelliJVersionDetector {

  private val IntellijVersionRegex = """\d+\.\d+(\.\d+)?""".r

  def detectIntellijVersion(intellijDirectory: File): Option[Version] = {
    val buildTxtContent = readFileContent(intellijDirectory)
    val fromBuildTxt = readVersionFromBuildTxt(buildTxtContent)
    val orFromIntelliJDirectory = fromBuildTxt.orElse(readVersionFromIntellijDirectory(intellijDirectory))
    orFromIntelliJDirectory
  }

  //build.txt example content: IC-222.3739.54, IU-222.3739.54
  def readVersionFromBuildTxt(buildTxtContent: String): Option[Version] = {
    val contentCleaned = buildTxtContent.trim.toLowerCase.stripPrefix("ic-").stripPrefix("iu-")
    parseVersion(contentCleaned)
  }

  //example path: ~/.samplePluginIC/sdk/222.3739.54
  def readVersionFromIntellijDirectory(intellijDirectory: File): Option[Version] =
    parseVersion(intellijDirectory.getName)

  private def readFileContent(intellijDirectory: File): String = {
    import sbt.io.syntax.fileToRichFile
    util.Using.resource(Source.fromFile(intellijDirectory / "build.txt"))(_.getLines().mkString("\n"))
  }

  private def parseVersion(versionString: String): Option[Version] =
    versionString match {
      case version@IntellijVersionRegex(_) =>
        Some(Version(version))
      case _ => None
    }
}
