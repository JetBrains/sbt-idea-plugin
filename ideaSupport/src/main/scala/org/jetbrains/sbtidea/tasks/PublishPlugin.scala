package org.jetbrains.sbtidea.tasks

import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.client5.http.impl.classic.{CloseableHttpClient, HttpClients}
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.message.StatusLine.StatusClass
import org.apache.hc.core5.http.{ClassicHttpResponse, ContentType, HttpEntity}
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import org.jetbrains.sbtidea.packaging.PackagingKeys.{packageArtifactZip, packageOutputDir}
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger, tasks}
import sbt.*
import sbt.Keys.streams

import java.nio.file.Files
import scala.util.Using

object PublishPlugin extends SbtIdeaInputTask[Unit] {

  final private val TOKEN_FILENAME  = ".ij-plugin-repo-token"
  final private val TOKEN_KEY       = "IJ_PLUGIN_REPO_TOKEN"

  private val MarketplaceUrl = "https://plugins.jetbrains.com"
  private val MarketplacePluginUploadUrl = s"$MarketplaceUrl/plugin/uploadPlugin"

  def apply(
    token: String,
    xmlId: String,
    channel: Option[String],
    pluginFile: File,
    log: PluginLogger
  ): Unit = {
    val fileLength = pluginFile.length
    log.info(s"Uploading ${pluginFile.getName}($fileLength bytes) to $MarketplaceUrl...")

    Using.resource(Files.newInputStream(pluginFile.toPath)) { pluginStream =>
      val multipartEntity = MultipartEntityBuilder.create()
        .addPart("file", new ProgressTrackingInputStreamBody(
          pluginStream,
          ContentType.create("application/zip"),
          pluginFile.getName,
          trackFunc = uploadCallback(fileLength)(_)
        ))
        .addTextBody("xmlId", xmlId)
        .addTextBody("channel", channel.getOrElse(""))
        .build()

      val httpPost = new HttpPost(MarketplacePluginUploadUrl)
      httpPost.addHeader("Authorization", s"Bearer $token")
      httpPost.setEntity(multipartEntity)

      val httpClient: CloseableHttpClient = HttpClients.createDefault
      httpClient.execute(httpPost, (response: ClassicHttpResponse) => {
        val entity: HttpEntity = response.getEntity
        val responseString: String = EntityUtils.toString(entity, "UTF-8")
        val httpCode = response.getCode
        if (StatusClass.from(httpCode) == StatusClass.SUCCESSFUL) {
          log.info(s"Successfully uploaded ${pluginFile.getName} to $MarketplaceUrl")
        } else {
          throw new IllegalStateException(s"Failed to upload plugin: ($httpCode) : $responseString")
        }
      })
    }
  }

  private def uploadCallback(fullLength: Long)(uploadedLength: Long): Unit = {
    if (isProgressSupported) {
      val uploadedPercent = ((uploadedLength.toDouble / fullLength) * 100).toInt
      print(s"\rProgress: $uploadedPercent%")

      if (uploadedLength >= fullLength) {
        println()
      }
    }
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
