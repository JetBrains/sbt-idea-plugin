package org.jetbrains.sbtidea.download.api

import java.io.File

import org.jetbrains.sbtidea.download.BuildInfo

trait InstallerFactory {
    def createInstaller(ideaInstallDir: File, buildInfo: BuildInfo): IdeaInstaller
  }