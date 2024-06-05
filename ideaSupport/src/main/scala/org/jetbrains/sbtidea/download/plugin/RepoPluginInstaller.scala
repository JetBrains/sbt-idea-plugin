package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.download.FileDownloader.DownloadException
import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.download.{BuildInfo, FileDownloader, IdeaUpdater, NioUtils, NotFoundHttpResponseCode, PluginXmlDetector, VersionComparatorUtil}
import org.jetbrains.sbtidea.{IntellijPlugin, PluginLogger as log}

import java.nio.file.{Files, Path}

class RepoPluginInstaller(buildInfo: BuildInfo)
                         (implicit repo: PluginRepoApi, localRegistry: LocalPluginRegistryApi) extends Installer[RemotePluginArtifact] {
  import RepoPluginInstaller.*

  override def isInstalled(art: RemotePluginArtifact)(implicit ctx: InstallContext): Boolean =
    !IdeaUpdater.isDumbPlugins &&
      localRegistry.isPluginInstalled(art.caller.plugin) &&
      isInstalledPluginUpToDate(art.caller.plugin)

  override def downloadAndInstall(art: RemotePluginArtifact)(implicit ctx: InstallContext): Unit = {
    val downloader = FileDownloader(ctx.baseDirectory.getParent)
    val downloadUrl = art.dlUrl
    val dist = try {
      downloader.download(downloadUrl)
    } catch {
      case de: DownloadException if de.responseCode.contains(NotFoundHttpResponseCode) =>
        art.caller.plugin.fallbackDownloadUrl match {
          case Some(fallbackUrl) =>
            log.warn(s"Plugin not found at $downloadUrl\nTrying to download from a fallback url $fallbackUrl")
            downloader.download(fallbackUrl)
          case _  =>
            throw de
        }
    }
    installIdeaPlugin(art.caller.plugin, dist)
  }

  private[plugin] def installIdeaPlugin(plugin: IntellijPlugin, artifact: Path)(implicit ctx: InstallContext): Path = {
    val installedPluginRoot = if (!PluginXmlDetector.Default.isPluginJar(artifact)) {
      val tmpPluginDir = extractPluginToTemporaryDir(
        artifact,
        plugin,
        s"${buildInfo.edition.name}-${buildInfo.buildNumber}-plugin"
      )
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
    localRegistry.markPluginInstalled(plugin, installedPluginRoot)
    installedPluginRoot
  }

  private[plugin] def isInstalledPluginUpToDate(plugin: IntellijPlugin)(implicit ctx: InstallContext): Boolean = {
    val pluginRoot = localRegistry.getInstalledPluginRoot(plugin)
    val descriptor = LocalPluginRegistry.extractInstalledPluginDescriptorFileContent(pluginRoot)
    descriptor match {
      case Left(error) =>
        log.warn(s"Failed to extract descriptor from plugin $plugin: $error")
        log.info(s"Up-to-date check failed, assuming plugin $plugin is out of date")
        return false
      case Right(data) =>
        val descriptor = PluginDescriptor.load(data.content)
        if (!isPluginCompatibleWithIdea(descriptor)) {
          log.warn(s"Plugin $plugin is incompatible with current ideaVersion(${buildInfo.buildNumber}): $descriptor")
          return false
        }
        plugin match {
          case IntellijPlugin.Id(_, Some(version), _, _) if descriptor.version != version =>
            log.info(s"Locally installed plugin $plugin has different version: ${descriptor.version} != $version")
            return false
          case IntellijPlugin.Id(_, None, channel, _) =>
            getMoreUpToDateVersion(descriptor, channel) match {
              case None =>
              case Some(newVersion) =>
                log.warn(s"Newer version of plugin $plugin is available: ${descriptor.version} -> $newVersion")
                return false
            }
          case _ =>
        }
    }
    true
  }

  private[plugin] def getMoreUpToDateVersion(descriptor: PluginDescriptor, channel: Option[String]): Option[String] = {
    repo.getLatestPluginVersion(buildInfo, descriptor.id, channel) match {
      case Right(version) if VersionComparatorUtil.compare(descriptor.version, version) < 0 =>
        Some(version)
      case Left(error) =>
        log.warn(s"Failed to fetch latest plugin ${descriptor.id} version: $error")
        None
      case _ => None
    }
  }

  private[plugin] def isPluginCompatibleWithIdea(metadata: PluginDescriptor)(implicit ctx: InstallContext): Boolean = {
    val lower = metadata.sinceBuild.replaceAll("^.+-", "") // strip IC- / PC- etc. prefixes
    val upper = metadata.untilBuild.replaceAll("^.+-", "")
    val actualIdeaBuild = ctx.productInfo.buildNumber
    val lowerValid = compareIdeaVersions(lower, actualIdeaBuild) <= 0
    val upperValid = compareIdeaVersions(upper, actualIdeaBuild) >= 0
    lowerValid && upperValid
  }

  private def pluginsDir(implicit ctx: InstallContext): Path = ctx.baseDirectory.resolve("plugins")
}

object RepoPluginInstaller {
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
      if (a_i == b_i && a_i == SNAPSHOT_VALUE) return 0
      if (a_i == SNAPSHOT_VALUE) return 1
      if (b_i == SNAPSHOT_VALUE) return -1
      val res = a_i - b_i
      if (res != 0) return res
    }
    c1.length - c2.length
  }

  def extractPluginToTemporaryDir(
    pluginZip: Path,
    plugin: IntellijPlugin,
    tempDirectoryName: String
  )(implicit ctx: InstallContext): Path = {
    val extractDir = Files.createTempDirectory(ctx.baseDirectory, tempDirectoryName)
    log.info(s"Extracting plugin '$plugin to $extractDir")
    sbt.IO.unzip(pluginZip.toFile, extractDir.toFile)
    assert(Files.list(extractDir).count() == 1, s"Expected only single plugin folder in extracted archive, got: ${extractDir.toFile.list().mkString}")
    val tmpPluginDir = Files.list(extractDir).findFirst().get()
    tmpPluginDir
  }
}

