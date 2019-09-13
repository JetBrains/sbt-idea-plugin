package org.jetbrains.sbtidea.tasks

import java.io.InputStream

import org.jetbrains.sbtidea.PluginLogger
import sbt._
import scalaj.http._


object PublishPlugin {

  final val TOKEN_FILENAME  = ".ij-plugin-repo-token"
  final val TOKEN_KEY       = "IJ_PLUGIN_REPO_TOKEN"

  def apply(token: String, pluginId: String, channel: Option[String], pluginFile: File, log: PluginLogger): Unit = {
    val host = "https://plugins.jetbrains.com"
    log.info(s"Uploading ${pluginFile.getName}(${pluginFile.length} bytes) to $host...")
    sbt.jetbrains.ideaPlugin.apiAdapter.Using.fileInputStream(pluginFile) { pluginStream =>
      val response = Http(s"$host/plugin/uploadPlugin")
        .timeout(connTimeoutMs = 5000, readTimeoutMs = 60000)
        .postForm(Seq(
          "pluginId" -> pluginId,
          "channel"  -> channel.getOrElse("")
        ))
        .header("Authorization", s"Bearer $token")
        .postMulti(createMultipartData(pluginFile, pluginStream))
          .asString

      if (response.isError) {
        log.error(s"Failed to upload plugin")
        log.error(s"(${response.code}) : ${response.body}")
      } else {
        log.info(s"Successfully uploaded ${pluginFile.name} to $host")
      }
    }
  }

  private def createMultipartData(pluginFile: File, pluginInputStream: InputStream): MultiPart =
    MultiPart("file", pluginFile.getName, "application/zip", pluginInputStream, pluginFile.length(), uploadCallback(pluginFile.length))

  private def uploadCallback(fullLength: Long)(uploadedLength: Long): Unit =
    if (isProgressSupported) {
      val uploadedPercent = ((uploadedLength.toDouble / fullLength) * 100).toInt
      print(s"\rProgress: $uploadedPercent%")
      if (uploadedLength >= fullLength)
        println()
    }

  private def isProgressSupported: Boolean =
    jline.TerminalFactory.get.isAnsiSupported
}
