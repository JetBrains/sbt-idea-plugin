package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.{IdeInstallationContext, IdeInstallationProcessContext, Installer}
import org.jetbrains.sbtidea.download.idea.IdeaSourcesImpl.SOURCES_ZIP
import org.jetbrains.sbtidea.download.{FileDownloader, NioUtils}
import org.jetbrains.sbtidea.{PathExt, PluginLogger}
import sbt.pathToPathOps

import java.nio.file.Files

class IdeaSourcesInstaller(caller: AbstractIdeaDependency) extends Installer[IdeaSources] {

  override def isInstalled(art: IdeaSources)(implicit ctx: IdeInstallationContext): Boolean =
    (ctx.baseDirectory / SOURCES_ZIP).exists

  override def downloadAndInstall(art: IdeaSources)(implicit ctx: IdeInstallationProcessContext): Unit = {
    val file = FileDownloader(ctx).download(art.dlUrl, optional = true)
    Files.copy(file, ctx.baseDirectory.resolve(SOURCES_ZIP))
    if (!keepDownloadedFiles) {
      NioUtils.delete(file)
    }
    PluginLogger.info(s"${caller.buildInfo.edition.name} sources installed")
  }
}
