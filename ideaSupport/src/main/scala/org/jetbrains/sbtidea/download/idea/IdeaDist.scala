package org.jetbrains.sbtidea.download.idea

import java.net.URL

import org.jetbrains.sbtidea.download.api._

sealed trait IdeaDist extends IdeaArtifact

case class IdeaDistImpl(caller: AbstractIdeaDependency, dlUrl: URL) extends IdeaDist {
  override type R = IdeaDist
  override protected def usedInstaller: Installer[IdeaDist] = new IdeaDistInstaller(caller.buildInfo)
}
