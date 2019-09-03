package org.jetbrains.sbtidea.download

import java.io.File

import org.jetbrains.sbtidea.Keys.IdeaPlugin
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.api.{IdeaArtifactResolver, InstallerFactory}
import sbt._


/**
  *
  * @param resolver
  * @param installerFactory
  * @param ideaInstallDir directory with extracted distribution containing "lib", "bin" etc.
  * @param log
  */
class IdeaUpdater(private val resolver: IdeaArtifactResolver,
                  private val installerFactory: InstallerFactory,
                  val ideaInstallDir: File, log: PluginLogger) {

  private val downloader: FileDownloader = new FileDownloader(ideaInstallDir.getParentFile, log)

  //noinspection MapGetOrElseBoolean
  def updateIdeaAndPlugins(ideaBuildInfo: BuildInfo, plugins: Seq[IdeaPlugin], withSources: Boolean = true): File = {
    val dumbOptions = sys.props.get("IdeaUpdater.dumbMode").getOrElse("").toLowerCase
    val installRoot = if (!dumbOptions.contains("idea")) updateIdea(ideaBuildInfo) else new File("")
    if (!dumbOptions.contains("plugin")) updatePlugins(ideaBuildInfo, plugins)
    installRoot
  }

  private def updateIdea(buildInfo: BuildInfo): File = {
    val installer = installerFactory.createInstaller(ideaInstallDir, buildInfo)
    if (installer.isIdeaAlreadyInstalled)
      return installer.getInstallDir
    log.info(s"Resolving IDEA dependency for $buildInfo")
    val parts           = resolver.resolveUrlForIdeaBuild(buildInfo)
    log.info(s"Downloading ${parts.size} IDEA artifacts")
    val downloadedFiles = parts.map(p => p -> downloader.download(p))
    val installed       = installer.installIdeaDist(downloadedFiles)
    installed
  }

  private def updatePlugins(buildInfo: BuildInfo, plugins: Seq[IdeaPlugin]): Unit = {
    val installer = installerFactory.createInstaller(ideaInstallDir, buildInfo)
    def updatePlugin(plugin: IdeaPlugin): Unit = {
      if (installer.isPluginAlreadyInstalledAndUpdated(plugin))
        return
      log.info(s"Resolving plugin ${plugin.name}")
      val resolved = resolver.resolvePlugin(buildInfo, plugin)
      log.info(s"Downloading plugin ${plugin.name}")
      val artifact = downloader.download(resolved)
      installer.installIdeaPlugin(plugin, resolved, artifact)
    }
    plugins.foreach {
      updatePlugin
    }
  }
}