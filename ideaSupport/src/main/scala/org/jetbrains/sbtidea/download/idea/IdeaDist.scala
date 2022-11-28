package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.*

import java.net.URL

abstract class IdeaDist extends IdeaArtifact {
  override type R = IdeaDist
  override protected def usedInstaller: Installer[IdeaDist] = new IdeaDistInstaller(caller.buildInfo)
}

class IdeaDistImpl(override val caller: AbstractIdeaDependency, dlUrlProvider: () => URL) extends IdeaDist {
  override def dlUrl: URL = dlUrlProvider()
}

object IdeaDistImpl {
  def apply(caller: AbstractIdeaDependency, dlUrlProvider: () => URL): IdeaDistImpl = new IdeaDistImpl(caller, dlUrlProvider)
}