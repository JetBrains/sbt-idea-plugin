package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.*

import java.net.URL
import scala.language.postfixOps

abstract class IdeaSources extends IdeaArtifact {
  override type R = IdeaSources

  override protected def usedInstaller: Installer[IdeaSources] = new IdeaSourcesInstaller(caller)
}

class IdeaSourcesImpl(override val caller: AbstractIdeaDependency, dlUrlProvider: () => URL) extends IdeaSources {
  override def dlUrl: URL = dlUrlProvider()
}

object IdeaSourcesImpl {
  val SOURCES_ZIP = "sources.zip"

  def apply(caller: AbstractIdeaDependency, dlUrlProvider: () => URL): IdeaSourcesImpl = new IdeaSourcesImpl(caller, dlUrlProvider)
}