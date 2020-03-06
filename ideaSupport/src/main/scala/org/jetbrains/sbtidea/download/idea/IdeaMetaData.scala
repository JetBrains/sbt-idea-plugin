package org.jetbrains.sbtidea.download.idea

import java.net.URL
import java.nio.file.Path

import org.jetbrains.sbtidea.download.api._

case class IdeaMetaData(baseDirectory: Path, dlUrl: URL) extends IdeaArtifact {
  override type R = this.type
  override protected def usedInstaller: Installer[IdeaMetaData.this.type] = ???
  override def caller: IdeaDependency = ???
}