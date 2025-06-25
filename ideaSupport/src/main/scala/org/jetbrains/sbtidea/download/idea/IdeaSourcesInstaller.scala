package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.{IdeInstallationContext, IdeInstallationProcessContext, Installer}
import org.jetbrains.sbtidea.download.idea.IdeaSourcesInstaller.sourcesZipPath
import org.jetbrains.sbtidea.download.{BuildInfo, FileDownloader, NioUtils}
import org.jetbrains.sbtidea.{PathExt, PluginLogger}
import sbt.pathToPathOps

import java.nio.file.{Files, Path}

class IdeaSourcesInstaller(sourcesBuildInfo: BuildInfo) extends Installer[IdeaSources] {

  override def isInstalled(art: IdeaSources)(implicit ctx: IdeInstallationContext): Boolean =
    sourcesZipPath(ctx.baseDirectory, sourcesBuildInfo).exists

  override def downloadAndInstall(art: IdeaSources)(implicit ctx: IdeInstallationProcessContext): Unit = {
    val file = FileDownloader(ctx).downloadOptional(art.dlUrl) match {
      case Some(value) => value
      case None =>
        //the warning will be printed by the file downloader itself
        return
    }

    val targetFile = sourcesZipPath(ctx.baseDirectory, sourcesBuildInfo)

    Files.createDirectories(targetFile.getParent)
    Files.copy(file, targetFile)

    if (!keepDownloadedFiles) {
      NioUtils.delete(file)
    }

    PluginLogger.info(s"${targetFile.getFileName.toString} sources installed")
  }
}

object IdeaSourcesInstaller {
  //noinspection ScalaWeakerAccess (used in Scala Plugin repo)
  val SourcesRootDirName: String = "sources"

  def sourcesZipPath(intellijBaseDir: Path, buildInfo: BuildInfo): Path =
    sourcesRoot(intellijBaseDir) / platformSourcesArchiveName(buildInfo)

  def sourcesRoot(intellijBaseDir: Path): Path =
    intellijBaseDir / SourcesRootDirName

  /**
   * Example:
   *  - ideaIU-251.26927.23-sources.jar
   *  - ideaIC-251.26927-EAP-CANDIDATE-SNAPSHOT-sources.jar
   */
  //noinspection ScalaWeakerAccess (used in Scala Plugin repo)
  def platformSourcesArchiveName(buildInfo: BuildInfo): String =
    s"${buildInfo.edition.name}-${buildInfo.buildNumber}-sources.zip"
}
