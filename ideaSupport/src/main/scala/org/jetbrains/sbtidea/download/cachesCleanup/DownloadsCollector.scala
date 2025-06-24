package org.jetbrains.sbtidea.download.cachesCleanup

import java.nio.file.{Files, Path}

object DownloadsCollector {

  def collectDownloads(downloadsDirectory: Path): DownloadsReport = {
    val fileInfos = FileUtils.collectFiles(
      downloadsDirectory, 
      filter = Files.isRegularFile(_)
    )
    DownloadsReport(fileInfos, downloadsDirectory)
  }
}