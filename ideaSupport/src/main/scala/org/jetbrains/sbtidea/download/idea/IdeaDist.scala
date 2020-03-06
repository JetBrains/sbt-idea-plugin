package org.jetbrains.sbtidea.download.idea

import java.net.URL

import org.jetbrains.sbtidea.download.api._

case class IdeaDist(caller: IdeaDependency, dlUrl: URL) extends IdeaArtifact {
  override type R = IdeaDist
  override protected def usedInstaller: Installer[IdeaDist] = new IdeaDistInstaller(caller.buildInfo)
}
