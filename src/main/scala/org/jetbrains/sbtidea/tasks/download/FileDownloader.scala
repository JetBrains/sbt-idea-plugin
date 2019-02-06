package org.jetbrains.sbtidea.tasks.download

import sbt.{File, IO, Logger}

private class FileDownloader(private val baseDirectory: File)(implicit val log: Logger) {

  private val downloadDirectory = getOrCreateDLDir()

  def download(artifactPart: ArtifactPart): File = {
    val partFile   = new File(downloadDirectory, s"${createName(artifactPart)}.part")
    val targetFile = new File(downloadDirectory, createName(artifactPart))
    if (targetFile.exists() && targetFile.length() > 0) {
      log.info(s"Already downloaded $artifactPart")
      return targetFile
    }
    partFile.delete()
    targetFile.delete()
    try {
      downloadViaSbt(artifactPart.url, partFile)
      partFile.renameTo(targetFile)
      targetFile
    } catch {
      case e: Exception if artifactPart.optional =>
        log.warn(s"Can't download optional ${artifactPart.url}: $e")
        new File("")
    }
  }

  private def createName(artifactPart: ArtifactPart): String =
    if (artifactPart.nameHint.nonEmpty)
      artifactPart.nameHint
    else
      Math.abs(artifactPart.url.hashCode).toString

  // TODO: resume interrupted downloads
  // TODO: report downloading progress to console
  private def downloadViaSbt(from: sbt.URL, to: File): Unit = {
    import sbt.jetbrains.ideaPlugin.apiAdapter._
    log.info(s"Downloading $from to $to")
    Using.urlInputStream(from) { inputStream =>
      IO.transfer(inputStream, to)
    }
  }

  // TODO: add downloading to temp if available
  private def getOrCreateDLDir(): File = {
    val dir = new File(baseDirectory, "downloads")
    if (!dir.exists())
      dir.mkdirs()
    dir
  }
}
