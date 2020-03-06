package org.jetbrains.sbtidea.download.plugin
import java.nio.file.{Files, Path}

import org.jetbrains.sbtidea.download.{BuildInfo, FileDownloader, IdeaUpdater, LocalPluginRegistry, NioUtils, PluginRepoUtils, PluginXmlDetector, VersionComparatorUtil}
import org.jetbrains.sbtidea.download.LocalPluginRegistry.{extractInstalledPluginDescriptor, extractPluginMetaData}
import org.jetbrains.sbtidea.download.api._
import org.jetbrains.sbtidea.{PluginLogger => log}
import org.jetbrains.sbtidea.Keys.IntellijPlugin


class PluginInstaller(buildInfo: BuildInfo) extends Installer[PluginArtifact] {
  import PluginInstaller._

  override def isInstalled(art: PluginArtifact)(implicit ctx: InstallContext): Boolean =
    !IdeaUpdater.isDumbPlugins &&
      LocalPluginRegistry.instanceFor(ctx.baseDirectory).isPluginInstalled(art.caller.plugin) &&
      isInstalledPluginUpToDate(art.caller.plugin)

  override def downloadAndInstall(art: PluginArtifact)(implicit ctx: InstallContext): Unit = {
    val dist = FileDownloader(ctx.baseDirectory.getParent).download(art.dlUrl)
    installIdeaPlugin(art.caller.plugin, dist)
  }

  private[plugin] def installIdeaPlugin(plugin: IntellijPlugin, artifact: Path)(implicit ctx: InstallContext): Path = {
    val installedPluginRoot = if (!isPluginJar(artifact)) {
      val extractDir = Files.createTempDirectory(ctx.baseDirectory, s"${buildInfo.edition.name}-${buildInfo.buildNumber}-plugin")
      log.info(s"Extracting plugin '$plugin to $extractDir")
      sbt.IO.unzip(artifact.toFile, extractDir.toFile)
      assert(Files.list(extractDir).count() == 1, s"Expected only single plugin folder in extracted archive, got: ${extractDir.toFile.list().mkString}")
      val tmpPluginDir = Files.list(extractDir).findFirst().get()
      val installDir = pluginsDir.resolve(tmpPluginDir.getFileName)
      NioUtils.delete(installDir)
      Files.move(tmpPluginDir, installDir)
      NioUtils.delete(tmpPluginDir.getParent)
      log.info(s"Installed plugin '$plugin to $installDir")
      installDir
    } else {
      val targetJar = pluginsDir.resolve(artifact.getFileName)
      Files.move(artifact, targetJar)
      log.info(s"Installed plugin '$plugin to $targetJar")
      targetJar
    }
    LocalPluginRegistry.instanceFor(ctx.baseDirectory).markPluginInstalled(plugin, installedPluginRoot)
    installedPluginRoot
  }

  private[plugin] def isInstalledPluginUpToDate(plugin: IntellijPlugin)(implicit ctx: InstallContext): Boolean = {
    val pluginRoot = LocalPluginRegistry.instanceFor(ctx.baseDirectory).getInstalledPluginRoot(plugin)
    val descriptor = extractInstalledPluginDescriptor(pluginRoot)
    descriptor match {
      case Left(error) =>
        log.warn(s"Failed to extract descriptor from plugin $plugin: $error")
        log.info(s"Up-to-date check failed, assuming plugin $plugin is out of date")
        return false
      case Right(data) =>
        val metadata = extractPluginMetaData(data)
        if (!isPluginCompatibleWithIdea(metadata)) {
          log.warn(s"Plugin $plugin is incompatible with current ideaVersion(${buildInfo.buildNumber}): $metadata")
          return false
        }
        plugin match {
          case IntellijPlugin.Id(_, Some(version), _) if metadata.version != version =>
            log.info(s"Locally installed plugin $plugin has different version: ${metadata.version} != $version")
            return false
          case IntellijPlugin.Id(_, None, channel) =>
            getMoreUpToDateVersion(metadata, channel.getOrElse("")) match {
              case None =>
              case Some(newVersion) =>
                log.warn(s"Newer version of plugin $plugin is available: ${metadata.version} -> $newVersion")
                return false
            }
          case _ =>
        }
    }
    true
  }

  private[plugin] def getMoreUpToDateVersion(metadata: PluginMetadata, channel: String): Option[String] = {
    PluginRepoUtils.getLatestPluginVersion(buildInfo, metadata.id, channel) match {
      case Right(version) if VersionComparatorUtil.compare(metadata.version, version) < 0 =>
        Some(version)
      case Left(error) =>
        log.warn(s"Failed to fetch latest plugin ${metadata.id} version: $error")
        None
      case _ => None
    }
  }

  private[plugin] def isPluginCompatibleWithIdea(metadata: PluginMetadata): Boolean = {
    val lower = metadata.sinceBuild.replaceAll("^.+-", "") // strip IC- / PC- etc. prefixes
    val upper = metadata.untilBuild.replaceAll("^.+-", "")
    val lowerValid = compareIdeaVersions(lower, buildInfo.buildNumber) <= 0
    val upperValid = compareIdeaVersions(upper, buildInfo.buildNumber) >= 0
    lowerValid && upperValid
  }

  private[plugin] def isPluginJar(artifact: Path): Boolean = {
    val detector = new PluginXmlDetector
    detector.test(artifact)
  }

  private def pluginsDir(implicit ctx: InstallContext): Path = ctx.baseDirectory.resolve("plugins")
}

object PluginInstaller {
  // sort of copied from com.intellij.openapi.util.BuildNumber#compareTo
  def compareIdeaVersions(a: String, b: String): Int = {
    val SNAPSHOT        = "SNAPSHOT"
    val SNAPSHOT_VALUE  = Int.MaxValue

    val c1 = a.replaceAll(SNAPSHOT, SNAPSHOT_VALUE.toString).replaceAll("\\*", SNAPSHOT_VALUE.toString).split('.')
    val c2 = b.replaceAll(SNAPSHOT, SNAPSHOT_VALUE.toString).replaceAll("\\*", SNAPSHOT_VALUE.toString).split('.')
    val pairs = c1
      .zipAll(c2, "0", "0")
      .map(x => x._1.toInt -> x._2.toInt)

    for ((a_i, b_i) <- pairs) {
      if ((a_i == b_i) && (a_i.toInt == SNAPSHOT_VALUE)) return 0
      if (a_i == SNAPSHOT_VALUE) return 1
      if (b_i == SNAPSHOT_VALUE) return -1
      val res = a_i - b_i
      if (res != 0) return res
    }
    c1.length - c2.length
  }
}

