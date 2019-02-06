package org.jetbrains.sbtidea.tasks.download

import java.io.File

import org.jetbrains.sbtidea.tasks.download.api.{IdeaInstaller, InstallerFactory}
import sbt.Logger

class CommunityIdeaUpdater(ideaInstallDir: File)(implicit log: Logger) extends IdeaUpdater(
  ideaInstallDir     = ideaInstallDir,
  resolver          = new JBRepoArtifactResolver with IdeaPluginResolver,
  installerFactory  = new InstallerFactory {
    override def createInstaller(ideaInstallDir: File, buildInfo: BuildInfo): IdeaInstaller =
      new CommunityIdeaInstaller(ideaInstallDir, buildInfo)(log)
  }
)
