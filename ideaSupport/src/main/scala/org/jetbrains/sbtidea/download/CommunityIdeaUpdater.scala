package org.jetbrains.sbtidea.download

import java.nio.file.Path

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.api.{IdeaArtifactResolver, IdeaInstaller, InstallerFactory}

//noinspection ConvertExpressionToSAM : no SAM in scala 2.10
class CommunityIdeaUpdater(ideaInstallDir: Path, logger: PluginLogger) extends IdeaUpdater(
  ideaInstallDir     = ideaInstallDir,
  resolver          = new IdeaArtifactResolver with JBIdeaRepoArtifactResolver with JBPluginRepoResolver {
    override protected def log: PluginLogger = logger
  },
  installerFactory  = new InstallerFactory {
    override def createInstaller(ideaInstallDir: Path, buildInfo: BuildInfo): IdeaInstaller =
      new CommunityIdeaInstaller(ideaInstallDir, buildInfo, logger)
  },
  log = logger
)
