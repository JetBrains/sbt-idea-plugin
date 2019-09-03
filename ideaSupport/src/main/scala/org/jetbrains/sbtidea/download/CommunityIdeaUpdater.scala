package org.jetbrains.sbtidea.download

import java.io.File

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.api.{IdeaInstaller, InstallerFactory}

//noinspection ConvertExpressionToSAM : no SAM in scala 2.10
class CommunityIdeaUpdater(ideaInstallDir: File, log: PluginLogger) extends IdeaUpdater(
  ideaInstallDir     = ideaInstallDir,
  resolver          = new JBRepoArtifactResolver with IdeaPluginResolver,
  installerFactory  = new InstallerFactory {
    override def createInstaller(ideaInstallDir: File, buildInfo: BuildInfo): IdeaInstaller =
      new CommunityIdeaInstaller(ideaInstallDir, buildInfo, log)
  },
  log = log
)
