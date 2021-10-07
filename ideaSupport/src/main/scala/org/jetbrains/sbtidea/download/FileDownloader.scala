package org.jetbrains.sbtidea.download

import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{Files, Path, Paths}
import org.jetbrains.sbtidea.download.FileDownloader.ProgressInfo
import org.jetbrains.sbtidea.{PluginLogger => log, _}

import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, DurationInt}

class FileDownloader(private val baseDirectory: Path) {

  type ProgressCallback = (ProgressInfo, Path) => Unit

  private case class RemoteMetaData(length: Long, fileName: String)
  class DownloadException(message: String) extends RuntimeException(message)

  private val downloadDirectory = getOrCreateDLDir()

  @throws(classOf[DownloadException])
  def download(url: URL, optional: Boolean = false): Path = try {
    val partFile = downloadNative(url) { case (progressInfo, to) =>
        val text = s"\r${progressInfo.renderAll} -> $to"
        if (!progressInfo.done) print(text) else println(text)
    }
    val targetFile = partFile.getParent.resolve(partFile.getFileName.toString.replace(".part", ""))
    if (targetFile.toFile.exists()) {
      log.warn(s"$targetFile already exists, recovering failed install...")
      Files.delete(targetFile)
    }
    Files.move(partFile, targetFile)
    targetFile
  } catch {
    case e: Exception if optional =>
      log.warn(s"Can't download optional $url: $e")
      Paths.get("")
  }

  // TODO: add downloading to temp if available
  private def getOrCreateDLDir(): Path = {
    val dir = baseDirectory.resolve("downloads")
    if (!dir.toFile.exists())
      Files.createDirectories(dir)
    dir
  }

  private def downloadNative(url: URL)(progressCallback: ProgressCallback): Path = {
    val connection = url.openConnection()
    val remoteMetaData = getRemoteMetaData(url)
    val to =
      if (remoteMetaData.fileName.nonEmpty)
        downloadDirectory.resolve(remoteMetaData.fileName + ".part")
      else
        downloadDirectory.resolve(Math.abs(url.hashCode()).toString + ".part")
    val localLength = if (to.toFile.exists()) Files.size(to) else 0

    if (remoteMetaData.length == localLength) {
      log.warn(s"Part file already downloaded: recovering interrupted install...")
      return to
    }

    if (to.toFile.exists() && isResumeSupported(url)) {
      connection.setRequestProperty("Range", s"bytes=$localLength-")
      log.info(s"Resuming download of $url to $to")
    } else {
      Files.deleteIfExists(to)
      log.info(s"Starting download $url to $to")
    }

    var inChannel: ReadableByteChannel = null
    var outStream: FileOutputStream    = null
    try {
      val blockSize = 1 << 24 // 16M
      var transferred = -1L
      inChannel = Channels.newChannel(connection.getInputStream)
      outStream = new FileOutputStream(to.toFile, to.toFile.exists())
      val expectedFileSize = remoteMetaData.length
      val rbc = new RBCWrapper(inChannel, expectedFileSize, localLength, progressCallback, to)
      val fileChannel = outStream.getChannel
      var position = fileChannel.position()
      rbc.isOpen

      while (transferred != 0) {
        @tailrec
        def retryTransfer(retryAttempts: Int, retrySleep: Duration): Unit = {
          transferred = fileChannel.transferFrom(rbc, position, blockSize)
          position += transferred

          // `FileChannel.transferFrom` can sometimes return `0` even if the channel is not entirely read
          // looks like it depends on the (quality of?) connection
          if (transferred == 0
            && Files.size(to) != expectedFileSize
            && retryAttempts > 0
          ) {
            log.warn(s"transferred 0 bytes from input channel, retry...")
            Thread.sleep(retrySleep.toMillis)
            retryTransfer(retryAttempts - 1, retrySleep)
          }
        }

        retryTransfer(retryAttempts = InputChanelReadRetries, InputChanelReadRetryTimeout)
      }

      if (expectedFileSize > 0 && Files.size(to) != expectedFileSize) {
        throw new DownloadException(s"Incorrect downloaded file size: expected $expectedFileSize, got ${Files.size(to)}")
      }
      to
    } finally {
      try { if (inChannel != null) inChannel.close() } catch { case e: Exception => log.error(s"Failed to close input channel: $e") }
      try { if (outStream != null) outStream.close() } catch { case e: Exception => log.error(s"Failed to close output stream: $e") }
    }
  }

  private val InputChanelReadRetries = Option(System.getProperty("download.channel.read.retry.count")).map(_.toInt).getOrElse(10)
  private val InputChanelReadRetryTimeout = Option(System.getProperty("download.channel.read.retry.timeout.ms")).map(_.toInt.millis).getOrElse(1.second)

  private def isResumeSupported(url: URL): Boolean = withConnection(url) { connection =>
    try   { connection.getResponseCode != 206 }
    catch { case e: Exception => log.warn(s"Error checking for a resumed download: ${e.getMessage}"); false }
  }

  private def getRemoteMetaData(url: URL): RemoteMetaData = withConnection(url) { connection =>
    connection.setRequestMethod("HEAD")
    if (connection.getResponseCode >= 400)
      throw new DownloadException(s"Not found (404): $url")
    val contentLength = connection.getContentLength
    val nameFromHeader = java.net.URLDecoder
      .decode(
        connection
          .getHeaderField("Content-Disposition")
          .lift2Option
          .map(_.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1"))
          .getOrElse(""),
        "ISO-8859-1")
    val nameFromURL = url.toString.split("/").lastOption.getOrElse("")
    val name =
      if (nameFromHeader.nonEmpty)
        nameFromHeader
      else if (nameFromURL.isValidFileName)
        nameFromURL
      else
        Math.abs(url.hashCode()).toString
    RemoteMetaData(if (contentLength != 0) contentLength else -1, name)
  }


  class RBCWrapper(rbc: ReadableByteChannel, expectedSize: Long, alreadyDownloaded: Long, progressCallback: ProgressCallback, target: Path) extends ReadableByteChannel {
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
          progressCallback(ProgressInfo(percent.toInt, speed, readSoFar, expectedSize), target)
          lastTimeStamp  = newTimeStamp
          readLastSecond = 0
        }
      }
      numRead
    }
  }

}

object FileDownloader {

  def apply(baseDirectory: Path): FileDownloader = new FileDownloader(baseDirectory)

  case class ProgressInfo(percent: Int, speed: Double, downloaded: Long, total: Long) {
    def renderBar: String = {
      val width = jline.TerminalFactory.get().getWidth / 4 // quarter width for a progressbar is fine
      renderBar(width, '=', '.')
    }

    def renderBar(width: Int, doneChar: Char, leftChar: Char): String = {
      val inner = if (percent == 100)
        doneChar.toString * width
      else {
        val done = percent * width / 100
        val curr = 1
        val left = width - done - curr

        val doneStr = doneChar.toString * done
        val currStr = ">"
        val leftStr = leftChar.toString * left

        s"$doneStr$currStr$leftStr"
      }
      s"[$inner]"
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
}