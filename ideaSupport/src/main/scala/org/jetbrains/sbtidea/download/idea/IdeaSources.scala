package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.download.idea.IdeaSourcesImpl.SOURCES_ZIP
import org.jetbrains.sbtidea.download.{FileDownloader, NioUtils}
import org.jetbrains.sbtidea.{PathExt, PluginLogger}
import sbt.*

import java.net.URL
import java.nio.file.Files
import scala.language.postfixOps

abstract class IdeaSources extends IdeaArtifact {
  override type R = IdeaSources
  override protected def usedInstaller: Installer[IdeaSources] = new Installer[IdeaSources] {
    override def isInstalled(art: IdeaSources)(implicit ctx: InstallContext): Boolean =
      (ctx.baseDirectory / SOURCES_ZIP).exists
    override def downloadAndInstall(art: IdeaSources)(implicit ctx: InstallContext): Unit = {
      val file = FileDownloader(ctx.baseDirectory.getParent).download(art.dlUrl, optional = true)
      Files.copy(file, ctx.baseDirectory.resolve(SOURCES_ZIP))
      if (!keepDownloadedFiles) {
        NioUtils.delete(file)
      }
      PluginLogger.info(s"${caller.buildInfo.edition.name} sources installed")
    }
  }
}

class IdeaSourcesImpl(override val caller: AbstractIdeaDependency, dlUrlProvider: () => URL) extends IdeaSources {
  override def dlUrl: URL = dlUrlProvider()
}

object IdeaSourcesImpl {
  val SOURCES_ZIP = "sources.zip"
  def apply(caller: AbstractIdeaDependency, dlUrlProvider: () => URL): IdeaSourcesImpl = new IdeaSourcesImpl(caller, dlUrlProvider)
}