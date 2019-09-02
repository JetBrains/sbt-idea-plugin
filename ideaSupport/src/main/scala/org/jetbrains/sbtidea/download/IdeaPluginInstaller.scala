package org.jetbrains.sbtidea.download

import java.io.File
import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import java.util.Collections
import java.util.function.Predicate
import java.util.zip.ZipFile

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.download.api.{IdeaInstaller, PluginMetadata}
import sbt._

import scala.xml.XML

trait IdeaPluginInstaller extends IdeaInstaller {

  override def isPluginAlreadyInstalledAndUpdated(plugin: IdeaPlugin): Boolean =
    (pluginDir(plugin).exists() || pluginFile(plugin).exists()) && isPluginUpToDate(plugin)

  protected def isPluginUpToDate(plugin: IdeaPlugin): Boolean = {
    val descriptor = extractPluginDescriptor(plugin)
    descriptor match {
      case Left(error) =>
        log.warn(s"Failed to extract descriptor from plugin $plugin: $error")
        log.info(s"Up-to-date check failed, assuming plugin $plugin is out of date")
        false
      case Right(data) =>
        val channel = extractPluginChannel(plugin)
        val metadata = extractPluginMetaData(data).copy(channel = channel)
        if (!isPluginCompatibleWithIdea(metadata)) {
          log.warn(s"Plugin $plugin is incompatible with current ideaVersion(${buildInfo.buildNumber}): $metadata")
          return false
        }
        getMoreUpToDateVersion(metadata, channel) match {
          case None =>
          case Some(newVersion) =>
            log.warn(s"Newer version of plugin $plugin is available: ${metadata.version} -> $newVersion")
            return false
        }
        true
    }
  }

  override def installIdeaPlugin(plugin: IdeaPlugin, artifactPart: ArtifactPart, file: File): File = {
    if (new ZipFile(file).entries().nextElement().getName == s"${plugin.name}/") { // zips have a single folder in root with the same name as the plugin
      val tmpPluginDir = getInstallDir.getParentFile / s"${buildInfo.edition.name}-${buildInfo.buildNumber}-${plugin.name}-TMP"
      val installDir = pluginDir(plugin)
      sbt.IO.delete(tmpPluginDir)
      log.info(s"Extracting plugin '${plugin.name} to $tmpPluginDir")
      sbt.IO.unzip(file, tmpPluginDir)
      sbt.IO.move(tmpPluginDir, installDir)
      sbt.IO.delete(file)
      log.info(s"Installed plugin '${plugin.name} to $installDir")
      installDir
    } else {
      val targetFile = pluginFile(plugin)
      sbt.IO.move(file, targetFile)
      log.info(s"Installed plugin '${plugin.name} to $targetFile")
      targetFile
    }
  }

  private def extractPluginChannel(plugin: IdeaPlugin): String = plugin match {
    case org.jetbrains.sbtidea.Keys.IdeaPlugin.Id(_, _, channel) => channel.getOrElse("")
    case _ => ""
  }

  private def getMoreUpToDateVersion(metadata: PluginMetadata, channel: String): Option[String] = {
    PluginRepoUtils.getLatestPluginVersion(buildInfo, metadata.id, channel) match {
      case Left(error) =>
        log.warn(s"Failed to fetch latest plugin ${metadata.id} version: $error")
        None
      case Right(version) => Some(version)
    }
  }

  private def isPluginCompatibleWithIdea(metadata: PluginMetadata): Boolean = {
    val lower = metadata.sinceBuild.replaceAll("^.+-", "") // strip IC- / PC- etc. prefixes
    val upper = metadata.untilBuild.replaceAll("^.+-", "")
    val lowerValid = compareVersions(lower, buildInfo.buildNumber) <= 0
    val upperValid = compareVersions(upper, buildInfo.buildNumber) >= 0
    lowerValid && upperValid
  }

  // sort of copied from com.intellij.openapi.util.BuildNumber#compareTo
  private def compareVersions(a: String, b: String): Int = {
    val SNAPSHOT        = "SNAPSHOT"
    val SNAPSHOT_VALUE  = Int.MaxValue

    val c1 = a.replaceAll(SNAPSHOT, SNAPSHOT_VALUE.toString).split('.')
    val c2 = b.replaceAll(SNAPSHOT, SNAPSHOT_VALUE.toString).split('.')
    val pairs = c1
      .zipAll(c2, "0", "0")
      .map(x => x._1.toInt -> x._2.toInt)

    for ((a_i, b_i) <- pairs) {
      if ((a_i == b_i) && (a_i.toInt == SNAPSHOT_VALUE)) return 0
      if (a_i == SNAPSHOT_VALUE) return -1
      if (b_i == SNAPSHOT_VALUE) return 1
      val res = a_i - b_i
      if (res != 0) return res
    }
    c1.length - c2.length
  }

  private def extractPluginDescriptor(plugin: IdeaPlugin): Either[String, String] = {
    val pf = pluginFile(plugin)
    if (pf.exists())
      Right(pf.toPath)
    val pDir = pluginDir(plugin)
    if (pDir.exists()) {
      val lib = pDir.toPath.resolve("lib")
      val detector = new PluginXmlDetector
      val result = Files
        .list(lib)
        .filter(detector)
        .findFirst()
      if (result.isPresent)
        return Right(detector.result)
    }
    Left(s"Couldn't detect main plugin jar: $plugin")
  }

  private class PluginXmlDetector extends Predicate[Path] {
    import org.jetbrains.sbtidea.packaging.artifact._
    private val MAP = Collections.emptyMap[String, Any]()
    var result: String = _
    override def test(t: Path): Boolean = {
      val uri = URI.create(s"jar:${t.toUri}")
      using(FileSystems.newFileSystem(uri, MAP)) { fs =>
        val maybePluginXml = fs.getPath("META-INF", "plugin.xml")
        if (Files.exists(maybePluginXml)) {
          result = new String(Files.readAllBytes(maybePluginXml))
          true
        } else { false }
      }
    }
  }

  private def extractPluginMetaData(data: String): PluginMetadata = {
    val pluginXml = XML.load(data)
    val id = (pluginXml \\ "id").text
    val version = (pluginXml \\ "version").text
    val since = (pluginXml \\ "idea-version").head.attributes("since-build").text
    val until = (pluginXml \\ "idea-version").head.attributes("until-build").text
    PluginMetadata(id = id, version = version, sinceBuild = since, untilBuild = until)
  }

  protected def pluginDir(plugin: IdeaPlugin): File = getInstallDir / "externalPlugins"

  protected def pluginFile(plugin: IdeaPlugin): File = getInstallDir / "externalPlugins" / s"${plugin.name}.jar"
}
