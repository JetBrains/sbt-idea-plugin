package org.jetbrains.sbtidea.tasks.download

import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{Channels, ReadableByteChannel}

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
    targetFile.delete()
    try {
      try {
        downloadNative(artifactPart.url, partFile) { progressInfo =>
          val text = s"${progressInfo.renderAll} -> $partFile\r"
          if (!progressInfo.done) print(text) else println(text)
        }
      } catch {
        case e: Exception =>
          log.warn(s"Smart download of ${artifactPart.url} failed, trying via sbt: $e")
          downloadViaSbt(artifactPart.url, partFile)
      }
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

  case class ProgressInfo(percent: Int, speed: Double, downloaded: Long, total: Long) {
    def renderBar: String = {
      val width = jline.TerminalFactory.get().getWidth / 4 // quarter width for a progressbar is fine
      s"[${"=" * ((percent * width / 100) - 1)}>${"." * (width-(percent * width / 100))}]"
    }

    def renderSpeed: String = {
      if (speed < 1024)                     "%.0f B/s".format(speed)
      else if (speed < 1024 * 1024)         "%.2f KB/s".format(speed / 1024.0)
      else if (speed < 1024 * 1024 * 1024)  "%.2f MB/s".format(speed / (1024.0 * 1024.0))
      else                                  "%.2f GB/s".format(speed / (1024.0 * 1024.0 * 1024.0))
    }

    private def space = if (percent == 100) "" else " "

    def renderText: String = s"$renderSpeed; ${(downloaded / (1024 * 1024)).toInt}/${(total / (1024 * 1024)).toInt}MB"

    def renderAll: String = s"$percent%$space $renderBar @ $renderText"

    def done: Boolean = downloaded == total
  }

  private def downloadNative(url: URL, to: File)(progressCallback: ProgressInfo => Unit): Unit = {
    val connection = url.openConnection()
    val localLength = to.length()
    if (to.exists() && isResumeSupported(url)) {
      connection.setRequestProperty("Range", s"bytes=$localLength-")
      log.info(s"Resuming download of $url to $to")
    } else {
      sbt.IO.delete(to)
      log.info(s"Starting download $url to $to")
    }

    var inChannel: ReadableByteChannel = null
    var outStream: FileOutputStream    = null
    try {
      val remoteLength = getContentLength(url)
      inChannel = Channels.newChannel(connection.getInputStream)
      outStream = new FileOutputStream(to, to.exists())
      val rbc   = new RBCWrapper(inChannel, remoteLength, localLength, progressCallback)
      outStream.getChannel.transferFrom(rbc, 0, Long.MaxValue)
    } finally {
      try { if (inChannel != null) inChannel.close() } catch { case e: Exception => log.error(s"Failed to close input channel: $e") }
      try { if (outStream != null) outStream.close() } catch { case e: Exception => log.error(s"Failed to close output stream: $e") }
    }
  }

  private def isResumeSupported(url: URL): Boolean = withConnection(url) { connection =>
    try   { connection.getResponseCode != 206 }
    catch { case e: Exception => log.warn(s"Error checking for a resumed download: ${e.getMessage}"); false }
  }

  private def getContentLength(url: URL): Int = withConnection(url) { connection =>
    try {
      connection.setRequestMethod("HEAD")
      val contentLength = connection.getContentLength
      if (contentLength != 0) contentLength else -1
    } catch {
      case e: Exception => log.warn(s"Failed to get file size for $url: ${e.getMessage}"); -1
    }
  }


  class RBCWrapper(rbc: ReadableByteChannel, expectedSize: Long, alreadyDownloaded: Long, progressCallback: ProgressInfo => Unit) extends ReadableByteChannel {
    private var readSoFar       = alreadyDownloaded
    private var lastTimeStamp   = System.currentTimeMillis()
    private var readLastSecond  = 0L
    override def isOpen: Boolean  = rbc.isOpen
    override def close(): Unit    = rbc.close()
    override def read(bb: ByteBuffer): Int = {
      var numRead = rbc.read(bb)
      if (numRead > 0) {
        readSoFar       += numRead
        readLastSecond  += numRead
        val newTimeStamp = System.currentTimeMillis()
        if (newTimeStamp - lastTimeStamp >= 1000 || readSoFar == expectedSize) { // update every second or on finish
          val percent = if (expectedSize > 0)
            readSoFar.toDouble / expectedSize.toDouble * 100.0
          else
            -1.0
          val speed = readLastSecond.toDouble / ((newTimeStamp - lastTimeStamp + 1) / 1000.0)
          progressCallback(ProgressInfo(percent.toInt, speed, readSoFar, expectedSize))
          lastTimeStamp  = newTimeStamp
          readLastSecond = 0
        }
      }
      numRead
    }
  }

}
