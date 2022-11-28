package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import org.jetbrains.sbtidea.packaging.PackagingKeys.{packageArtifactZip, packageOutputDir}
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger, tasks}
import sbt.*
import sbt.Keys.streams
import scalaj.http.*

import java.io.InputStream


object PublishPlugin extends SbtIdeaInputTask[Unit] {

  final private val TOKEN_FILENAME  = ".ij-plugin-repo-token"
  final private val TOKEN_KEY       = "IJ_PLUGIN_REPO_TOKEN"

  def apply(token: String, xmlId: String, channel: Option[String], pluginFile: File, log: PluginLogger): Unit = {
    val host = "https://plugins.jetbrains.com"
    log.info(s"Uploading ${pluginFile.getName}(${pluginFile.length} bytes) to $host...")
    sbt.jetbrains.ideaPlugin.apiAdapter.Using.fileInputStream(pluginFile) { pluginStream =>
      val response = Http(s"$host/plugin/uploadPlugin")
        .timeout(connTimeoutMs = 5000, readTimeoutMs = 60000)
        .postForm(Seq(
          "xmlId"   -> xmlId,
          "channel" -> channel.getOrElse("")
        ))
        .header("Authorization", s"Bearer $token")
        .postMulti(createMultipartData(pluginFile, pluginStream))
          .asString

      if (response.isError) {
        throw new IllegalStateException(s"Failed to upload plugin: (${response.code}) : ${response.body}")
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

  override def createTask: Def.Initialize[InputTask[Unit]] = Def.inputTaskDyn {
    import complete.DefaultParsers.*
    import org.jetbrains.sbtidea.Keys.{signPlugin, signPluginOptions}
    val log = new SbtPluginLogger(streams.value)
    val signingEnabled = signPluginOptions.value.enabled
    val maybeChannel = spaceDelimited("<channel>").parsed.headOption
    val tokenFile = file(s"${sys.props.get("user.home").getOrElse(".")}/$TOKEN_FILENAME")
    val fromEnv = sys.env.get(TOKEN_KEY)
    val fromProps = sys.props.get(TOKEN_KEY)
    val token =
      if(tokenFile.exists() && tokenFile.length() > 0)
        IO.readLines(tokenFile).headOption.getOrElse("")
      else {
        fromEnv.getOrElse(
          fromProps.getOrElse(throw new IllegalStateException(
            s"Plugin repo authorisation token not set. Please either put one into $tokenFile or set $TOKEN_KEY env or prop")
          )
        )
      }
    val pluginId = LocalPluginRegistry.extractPluginMetaData(packageOutputDir.value.toPath) match {
      case Left(error) => throw new IllegalStateException(s"Can't extract plugin id from artifact: $error")
      case Right(metadata) => metadata.id
    }
    if (signingEnabled) Def.task {
      tasks.PublishPlugin.apply(token, pluginId.repr, maybeChannel, signPlugin.value, log)
    } else Def.task {
      tasks.PublishPlugin.apply(token, pluginId.repr, maybeChannel, packageArtifactZip.value, log)
    }
  }
}
