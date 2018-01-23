package org.jetbrains.sbtidea.tasks

import java.io.InputStream

import org.jetbrains.sbtidea.Keys._
import sbt.Keys._
import sbt._

import scalaj.http.{Http, MultiPart}


object PublishPlugin {
  def apply(settings: PublishSettings, pluginFile: File, streams: TaskStreams): String = {
    val host = "https://plugins.jetbrains.com"
    streams.log.info(s"Uploading ${pluginFile.getName}(${pluginFile.length} bytes) to $host...")
    sbt.jetbrains.apiAdapter.Using.fileInputStream(pluginFile) { pluginStream =>
      Http(s"$host/plugin/uploadPlugin")
        .timeout(connTimeoutMs = 5000, readTimeoutMs = 60000)
        .postForm(createForm(settings))
        .postMulti(createMultipartData(pluginFile, pluginStream))
        .asString
        .throwError
        .location
        .getOrElse(throw new Error("Uploaded plugin location is not set."))
    }
  }

  private def createForm(settings: PublishSettings): Seq[(String, String)] = Seq(
      "pluginId" -> settings.pluginId,
      "userName" -> settings.username,
      "password" -> settings.password,
      "channel"  -> settings.channel.getOrElse("")
    )

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
