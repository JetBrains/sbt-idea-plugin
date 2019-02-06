package org.jetbrains.sbtidea.tasks.download.api

import java.io.File

import org.jetbrains.sbtidea.tasks.download.BuildInfo

trait InstallerFactory {
    def createInstaller(ideaInstallDir: File, buildInfo: BuildInfo): IdeaInstaller
  }