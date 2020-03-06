package org.jetbrains.sbtidea.download
//
//import java.nio.file.{Path, Paths}
//
//import org.jetbrains.sbtidea.Keys.IntellijPlugin
//import org.jetbrains.sbtidea.PluginLogger
//import org.jetbrains.sbtidea.download.api.{IdeaArtifactResolver, InstallerFactory}
//
//
///**
//  *
//  * @param resolver
//  * @param installerFactory
//  * @param ideaInstallDir directory with extracted distribution containing "lib", "bin" etc.
//  * @param log
//  */
//class IdeaUpdater(private val resolver: IdeaArtifactResolver,
//                  private val installerFactory: InstallerFactory,
//                  val ideaInstallDir: Path, implicit val log: PluginLogger) {
//import IdeaUpdater._
//
//  private val downloader: FileDownloader = new FileDownloader(ideaInstallDir.getParent, log)
//
//  //noinspection MapGetOrElseBoolean
//  def updateIdeaAndPlugins(ideaBuildInfo: BuildInfo, plugins: Seq[IntellijPlugin], withSources: Boolean = true): Path = {
//
//    val dumbOptions = sys.props.get(DUMB_KEY).getOrElse("").toLowerCase
//    val installRoot = if (!dumbOptions.contains(DUMB_KEY_IDEA)) updateIdea(ideaBuildInfo) else Paths.get("")
//    if (!dumbOptions.contains(DUMB_KEY_PLUGINS)) updatePlugins(ideaBuildInfo, plugins)
//    if (!dumbOptions.contains(DUMB_KEY_JBR) && ideaBuildInfo.jbrVersion.isDefined)
//      new JbrInstaller(
//        installRoot,
//        JbrInstaller.extractVersionFromIdea(installRoot)
//      ).downloadAndInstall(ideaBuildInfo)
//    installRoot
//  }
//
//  private def updateIdea(buildInfo: BuildInfo): Path = {
//    val installer = installerFactory.createInstaller(ideaInstallDir, buildInfo)
//    if (installer.isIdeaAlreadyInstalled)
//      return installer.getInstallDir
//    log.info(s"Resolving ${buildInfo.edition.name} dependency for $buildInfo")
//    val parts           = resolver.resolveUrlForIdeaBuild(buildInfo)
//    log.info(s"Downloading ${parts.size} ${buildInfo.edition.name} artifacts")
//    val downloadedFiles = parts.map(p => p -> downloader.download(p))
//    val installed       = installer.installIdeaDist(downloadedFiles)
//    installed
//  }
//
//  private def updatePlugins(buildInfo: BuildInfo, plugins: Seq[IntellijPlugin]): Unit = {
//    val installer = installerFactory.createInstaller(ideaInstallDir, buildInfo)
//    def updatePlugin(plugin: IntellijPlugin): Unit = {
//      if (installer.isPluginAlreadyInstalledAndUpdated(plugin))
//        return
//      val resolved = resolver.resolvePlugin(buildInfo, plugin)
//      val artifact = downloader.download(resolved)
//      installer.installIdeaPlugin(plugin, artifact)
//    }
//    plugins.foreach {
//      updatePlugin
//    }
//  }
//}
//
object IdeaUpdater {
  final val DUMB_KEY: String          = "IdeaUpdater.dumbMode"
  final val DUMB_KEY_IDEA: String     = "idea"
  final val DUMB_KEY_PLUGINS: String  = "plugins"
  final val DUMB_KEY_JBR: String      = "jbr"
  final val IJ_REPO_OVERRIDE: String  = "sbtidea.ijrepo"

  private def dumbOptions = sys.props.get(DUMB_KEY).getOrElse("").toLowerCase
  def isDumbIdea: Boolean = dumbOptions.contains(DUMB_KEY_IDEA)
  def isDumbPlugins: Boolean = dumbOptions.contains(DUMB_KEY_PLUGINS)
  def isDumbJbr: Boolean = dumbOptions.contains(DUMB_KEY_JBR)
}