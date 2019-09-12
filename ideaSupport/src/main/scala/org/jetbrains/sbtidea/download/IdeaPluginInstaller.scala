package org.jetbrains.sbtidea.download

import java.nio.file.{Files, Path, StandardCopyOption}

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.download.LocalPluginRegistry._
import org.jetbrains.sbtidea.download.api.{IdeaInstaller, PluginMetadata}

trait IdeaPluginInstaller extends IdeaInstaller {

  private val localPluginRegistry = new LocalPluginRegistry(getInstallDir, log)

  override def isPluginAlreadyInstalledAndUpdated(plugin: IdeaPlugin): Boolean =
    localPluginRegistry.isPluginInstalled(plugin) && isInstalledPluginUpToDate(plugin)

  protected def isInstalledPluginUpToDate(plugin: IdeaPlugin): Boolean = {
    val pluginRoot = localPluginRegistry.getInstalledPluginRoot(plugin)
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
          case IdeaPlugin.Id(_, Some(version), _) if metadata.version != version =>
            log.info(s"Locally installed plugin $plugin has different version: ${metadata.version} != $version")
            return false
          case IdeaPlugin.Id(_, None, channel) =>
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

  override def installIdeaPlugin(plugin: IdeaPlugin, artifact: Path): Path = {
    val installedPluginRoot = if (!isPluginJar(artifact)) {
      val extractDir = Files.createTempDirectory(getInstallDir, s"${buildInfo.edition.name}-${buildInfo.buildNumber}-plugin")
      log.info(s"Extracting plugin '$plugin to $extractDir")
      sbt.IO.unzip(artifact.toFile, extractDir.toFile)
      assert(Files.list(extractDir).count() == 1, s"Expected only single plugin folder in extracted archive, got: ${extractDir.toFile.list().mkString}")
      val tmpPluginDir = Files.list(extractDir).findFirst().get()
      val installDir = pluginsDir.resolve(tmpPluginDir.getFileName)
      NioUtils.delete(installDir)
      Files.move(tmpPluginDir, installDir, StandardCopyOption.ATOMIC_MOVE)
      NioUtils.delete(tmpPluginDir.getParent)
      log.info(s"Installed plugin '$plugin to $installDir")
      installDir
    } else {
      val targetJar = pluginsDir.resolve(artifact.getFileName)
      Files.move(artifact, targetJar, StandardCopyOption.ATOMIC_MOVE)
      log.info(s"Installed plugin '$plugin to $targetJar")
      targetJar
    }
    localPluginRegistry.markPluginInstalled(plugin, installedPluginRoot)
    installedPluginRoot
  }

  private def getMoreUpToDateVersion(metadata: PluginMetadata, channel: String): Option[String] = {
    PluginRepoUtils.getLatestPluginVersion(buildInfo, metadata.id, channel) match {
      case Right(version) if compareVersions(metadata.version, version) < 0 =>
        Some(version)
      case Left(error) =>
        log.warn(s"Failed to fetch latest plugin ${metadata.id} version: $error")
        None
      case _ => None
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



  private def isPluginJar(artifact: Path): Boolean = {
    val detector = new PluginXmlDetector
    detector.test(artifact)
  }

  protected def pluginsDir: Path = getInstallDir.resolve("plugins")
}
