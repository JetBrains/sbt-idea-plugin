package org.jetbrains.sbtidea.download.api

import java.nio.file.Path

import org.jetbrains.sbtidea.download.BuildInfo

trait InstallerFactory {
    def createInstaller(ideaInstallDir: Path, buildInfo: BuildInfo): IdeaInstaller
  }