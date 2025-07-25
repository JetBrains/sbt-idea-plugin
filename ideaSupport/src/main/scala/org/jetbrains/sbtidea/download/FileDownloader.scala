package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.download.FileDownloader.{DownloadException, ProgressInfo, fetchRemoteMetaData}
import org.jetbrains.sbtidea.download.api.IdeInstallationProcessContext
import org.jetbrains.sbtidea.{PluginLogger as log, *}

import java.io.{FileOutputStream, IOException}
import java.net.{SocketTimeoutException, URL}
import java.nio.ByteBuffer
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{Files, Path, Paths}
import javax.net.ssl.SSLException
import scala.concurrent.duration.{Duration, DurationInt, DurationLong}
import scala.util.{Failure, Success, Try}

/**
 * The directory to which the temporary files are downloaded.<br>
 * By default, the layout looks like this {{{
 *   - .MyPlugin
 *     - sdk
 *        - downloads
 *        - 243.23654.117
 * }}}
 *
 * @see [[org.jetbrains.sbtidea.Init.buildSettings]]
 */
class FileDownloader(private val downloadDirectory: Path) {

  private type ProgressCallback = (ProgressInfo, Path) => Unit

  // TODO: add downloading to temp if available
  locally {
    if (!downloadDirectory.toFile.exists()) {
      Files.createDirectories(downloadDirectory)
    }
  }

  private val FilePartSuffix = ".part"
  private def toFilePartPath(path: Path): Path = path.resolveSibling(path.getFileName.toString + FilePartSuffix)
  private def fromFilePartPath(path: Path): Path = path.resolveSibling(path.getFileName.toString.replace(FilePartSuffix, ""))

  private val printProgressToConsoleCallback: ProgressCallback = { case (progressInfo, to) =>
    //use carriage return to overwrite the previous progress line
    val text = s"\r${progressInfo.renderAll} -> $to"
    //until the progress is 100%, we don't print a trailing new line
    if (!progressInfo.done)
      System.out.print(text)
    else
      System.out.println(text)
  }

  @throws(classOf[DownloadException])
  @deprecated("use another `download` overloaded method or `downloadOptional`", since = "4.1.16")
  def download(url: URL, optional: Boolean): Path = {
    if (optional)
      downloadOptional(url).getOrElse(Path.of(""))
    else
      download(url)
  }

  @throws(classOf[DownloadException])
  def download(url: URL): Path = {
    downloadOptionallyReusingPartFile(url, optional = false).get.get
  }

  /**
   * @return Some file if the file was successfully downloaded<br>
   *         None if optional=true, and the file couldn't be downloaded due to an exception
   * @throws FileDownloader#DownloadException if optional=false, and the file couldn't be downloaded
   */
  @throws(classOf[DownloadException])
  def downloadOptional(url: URL, optional: Boolean = true): Option[Path] = {
    downloadOptionallyReusingPartFile(url, optional = optional).get
  }

  @throws(classOf[DownloadException])
  private def downloadOptionallyReusingPartFile(
    url: URL,
    optional: Boolean,
    reuseExistingPartFile: Boolean = true
  ): Try[Option[Path]] = Try {
    val partFile = downloadNativeWithConnectionRetry(url, reuseExistingPartFile)(printProgressToConsoleCallback)
    partFile match {
      case DownloadedPath.PartPath(partPath) =>
        val targetFile = fromFilePartPath(partPath)
        if (targetFile.toFile.exists()) {
          log.warn(s"$targetFile already exists, recovering failed install...")
          Files.delete(targetFile)
        }
        Files.move(partPath, targetFile)
        Some(targetFile)
      case DownloadedPath.ZipPath(path) =>
        Some(path)
    }
  }.recoverWith {
    case e: Exception =>
      val canNotDownloadFilePart = isRangeNotSatisfiableHttpResponseException(e)
      if (canNotDownloadFilePart && reuseExistingPartFile) {
        log.warn(s"Can't download file part $url: $e")
        log.warn(s"Retrying with a fresh download...")
        downloadOptionallyReusingPartFile(url, optional, reuseExistingPartFile = false)
      }
      else if (optional) {
        log.warn(s"Can't download optional file $url: $e")
        Success(None)
      }
      else {
        log.warn(s"Can't download file $url: $e")
        Failure(e)
      }
  }

  private def isRangeNotSatisfiableHttpResponseException(e: Exception) = e match {
    case ioe: IOException =>
      // Example:
      // [warn] Can't download optional <path>/ideaIC-251.26927T-sources.jar: java.io.IOException: Server returned HTTP response code: 416 for URL: <path>/ideaIC-251.26927T-sources.jar
      ioe.getMessage.toLowerCase.contains("http response code: 416") //from HttpURLConnection
    case _ =>
      false
  }

  private def SocketConnectionTimeoutMs = getPositiveLongProperty("download.socket.connection.timeout.ms", 5.seconds.toMillis).ensuring(_ <= Int.MaxValue).toInt
  private def SocketReadTimeoutMs = getPositiveLongProperty("download.socket.read.timeout.ms", 15.seconds.toMillis).ensuring(_ <= Int.MaxValue).toInt

  // This retry is required e.g. when build server (which contain IDEA artifact) is down for some reason (e.g. it's being restarted)
  // we need a longer timeout in order server has time to restart (FileDownloader supports resuming previous download)
  private def DownloadRetryConnectionTimeoutCount = getPositiveLongProperty("download.retry.connection.timeout.count", 3)
  private def DownloadRetryConnectionTimeoutWait = getPositiveLongProperty("download.retry.connection.timeout.wait.ms", 1.minute.toMillis).millis

  // This retry is required when there are some connection issues, e.g recently we observe some SSL issues, when connection is closed by the server
  // Ideally the connection should be stable and the SSL server should be fixed on the server side.
  // But since FileDownloader supports resuming previous download, this retry will not hurt
  private def DownloadRetryConnectionIssueCount = getPositiveLongProperty("download.retry.connection.issue.count", 5)
  private def DownloadRetryConnectionIssueWait = getPositiveLongProperty("download.retry.connection.issue.timeout.ms", 5.seconds.toMillis).millis

  private def getPositiveLongProperty(key: String, default: Long): Long =
    Option(System.getProperty(key)).map(_.toLong).getOrElse(default).ensuring(_ >= 0, s"key '$key' must be a non-negative value")

  private sealed trait DownloadedPath
  private object DownloadedPath {
    case class PartPath(path: Path) extends DownloadedPath
    case class ZipPath(path: Path) extends DownloadedPath
  }

  private def downloadNativeWithConnectionRetry(
    url: URL,
    reuseExistingPartFile: Boolean
  )(progressCallback: ProgressCallback): DownloadedPath = {
    val retry1Timeout: Duration = DownloadRetryConnectionTimeoutWait
    val retry2Timeout: Duration = DownloadRetryConnectionIssueWait

    var isFirstAttempt = true

    //noinspection NoTailRecursionAnnotation
    def inner(retries1: Long, retries2: Long): DownloadedPath = {
      try downloadNative(url, reuseExistingPartFile, isFirstAttempt = isFirstAttempt)(progressCallback)
      catch {
        //this can happen when server is restarted
        case exception @ (_: SocketTimeoutException | _: SSLException) if retries1 > 0 =>
          log.warn(s"Error occurred during download: ${exception.getMessage}, retry in $retry1Timeout ...")
          Thread.sleep(retry1Timeout.toMillis)
          inner(retries1 - 1, retries2)

        case downloadException: DownloadException =>
          //if we got "404: Not Found" response we can be pretty sure that it's not a network error and hte artefact doesn't exist
          if (retries2 <= 0 || downloadException.responseCode.contains(NotFoundHttpResponseCode))
            throw downloadException

          log.warn(s"Error occurred during download: ${downloadException.getMessage}, retry in $retry2Timeout ...")
          Thread.sleep(retry2Timeout.toMillis)
          inner(retries1, retries2 - 1)
      }
      finally {
        isFirstAttempt = false
      }
    }

    inner(
      DownloadRetryConnectionTimeoutCount,
      DownloadRetryConnectionIssueCount
    )
  }

  @throws[SocketTimeoutException]
  private def downloadNative(
    url: URL,
    reuseExistingPartFile: Boolean,
    isFirstAttempt: Boolean
  )(progressCallback: ProgressCallback): DownloadedPath = {
    val connection = url.openConnection()
    connection.setConnectTimeout(SocketConnectionTimeoutMs)
    connection.setReadTimeout(SocketReadTimeoutMs)

    val remoteMetaData = fetchRemoteMetaData(url)
    val fileNameWithoutPartSuffix =
      if (remoteMetaData.fileName.nonEmpty)
        remoteMetaData.fileName
      else
        Math.abs(url.hashCode()).toString

    val to = downloadDirectory.resolve(fileNameWithoutPartSuffix)
    val toLength = if (to.toFile.exists()) Files.size(to) else -1

    //The file can be present if this VM option was set in previous project import run:
    // `-Dsbt.idea.plugin.keep.downloaded.files`
    //
    //NOTE: file can be present but can have a different size for some idea versions
    //E.g. ideaIU-231.7515-EAP-CANDIDATE-SNAPSHOT can in reality represent different 231.7517.* versions
    //Because when new EAP-CANDIDATE is uploaded, the previous one is overridden
    if (remoteMetaData.length == toLength) {
      log.warn(s"File already downloaded: $to")
      return DownloadedPath.ZipPath(to)
    }

    val toPart = toFilePartPath(downloadDirectory.resolve(fileNameWithoutPartSuffix))
    val tpPartLength = if (toPart.toFile.exists()) Files.size(toPart) else -1

    if (remoteMetaData.length == tpPartLength) {
      log.warn(s"Part file already downloaded: recovering interrupted install...")
      return DownloadedPath.PartPath(toPart)
    }

    val resumePartFileDownload = toPart.toFile.exists() &&
      isResumeSupported(url) &&
      // reuseExistingPartFile is effective only during the first download try
      (reuseExistingPartFile || !isFirstAttempt)
    if (resumePartFileDownload) {
      connection.setRequestProperty("Range", s"bytes=$tpPartLength-")
      log.info(s"Resuming download of $url to $toPart")
    } else {
      Files.deleteIfExists(toPart)
      log.info(s"Starting download $url to $toPart")
    }

    var inChannel: ReadableByteChannel = null
    var outStream: FileOutputStream    = null
    try {
      val blockSize = 1 << 24 // 16M
      var transferred = -1L
      inChannel = Channels.newChannel(connection.getInputStream)
      outStream = new FileOutputStream(toPart.toFile, toPart.toFile.exists())
      val expectedFileSize = remoteMetaData.length
      val rbc = new RBCWrapper(inChannel, expectedFileSize, tpPartLength, progressCallback, toPart)
      val fileChannel = outStream.getChannel
      var position = fileChannel.position()

      while (transferred != 0) {
        transferred = fileChannel.transferFrom(rbc, position, blockSize)
        position += transferred
      }

      if (expectedFileSize > 0 && Files.size(toPart) != expectedFileSize) {
        throw new DownloadException(s"Incorrect downloaded file size: expected $expectedFileSize, got ${Files.size(toPart)}")
      } else {
        rbc.ensureFinalLogProgressCall()
      }
      DownloadedPath.PartPath(toPart)
    } finally {
      try { if (inChannel != null) inChannel.close() } catch { case e: Exception => log.error(s"Failed to close input channel: $e") }
      try { if (outStream != null) outStream.close() } catch { case e: Exception => log.error(s"Failed to close output stream: $e") }
    }
  }

  private def isResumeSupported(url: URL): Boolean = withConnection(url) { connection =>
    try   { connection.getResponseCode != 206 }
    catch { case e: Exception => log.warn(s"Error checking for a resumed download: ${e.getMessage}"); false }
  }

  class RBCWrapper(rbc: ReadableByteChannel, expectedSize: Long, alreadyDownloaded: Long, progressCallback: ProgressCallback, target: Path) extends ReadableByteChannel {
    private var readSoFar       = alreadyDownloaded
    private var lastTimeStamp   = System.nanoTime()
    private var readLastSecond  = 0L

    override def isOpen: Boolean  = rbc.isOpen
    override def close(): Unit    = rbc.close()
    override def read(bb: ByteBuffer): Int = {
      var numRead = rbc.read(bb)
      if (numRead > 0) {
        readSoFar       += numRead
        readLastSecond  += numRead

        maybeFlushProgressOutput(readSoFar)
      }
      numRead
    }

    private def maybeFlushProgressOutput(_readSoFar: Long): Unit = {
      val newTimeStamp = System.nanoTime()
      val duration = Duration.fromNanos(newTimeStamp) - Duration.fromNanos(lastTimeStamp)

      if (duration >= 1.second || _readSoFar == expectedSize) { // update every second or on finish
        val percent = if (expectedSize > 0)
          _readSoFar.toDouble / expectedSize.toDouble * 100.0
        else
          -1.0
        val speed = readLastSecond.toDouble / ((duration + 1.millisecond).toMillis / 1000.0)
        progressCallback(ProgressInfo(percent.toInt, speed, _readSoFar, expectedSize), target)
        lastTimeStamp = newTimeStamp
        readLastSecond = 0
      }
    }

    // Sometimes there might be an off-by-1-byte difference between these two
    // (potentially due to https://stackoverflow.com/a/263127?)
    // It's not reproduced for all files, though...
    // We want to ensure that 100% progress output is reported, thus we pretend that we read all bytes by enforcing readSoFar
    // It also ensures that a new line is printed after the progress
    private[FileDownloader] def ensureFinalLogProgressCall(): Unit = {
      if (readSoFar < expectedSize) {
        maybeFlushProgressOutput(_readSoFar = expectedSize)
      }
    }
  }

}

object FileDownloader {
  class DownloadException(message: String, val responseCode: Option[Int] = None) extends RuntimeException(message)

  def apply(ctx: IdeInstallationProcessContext): FileDownloader = new FileDownloader(ctx.artifactsDownloadsDir)

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

  case class RemoteMetaData(length: Long, fileName: String)

  private def fetchRemoteMetaData(url: URL): RemoteMetaData = withConnection(url) { connection =>
    connection.setRequestMethod("HEAD")
    val responseCode = connection.getResponseCode
    if (responseCode >= 400) {
      val message = connection.getResponseMessage
      throw new DownloadException(s"$message ($responseCode): $url", Some(responseCode))
    }
    val contentLength = connection.getContentLengthLong
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
}