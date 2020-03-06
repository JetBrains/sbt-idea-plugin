package org.jetbrains.sbtidea.download.api

import java.nio.file.Path

case class InstallContext(baseDirectory: Path, downloadDirectory: Path)