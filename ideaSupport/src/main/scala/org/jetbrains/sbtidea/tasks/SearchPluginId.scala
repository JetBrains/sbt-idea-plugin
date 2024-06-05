package org.jetbrains.sbtidea.tasks

import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.jetbrains.sbtidea.Keys.{intellijBaseDirectory, intellijBuild, intellijPlatform}
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.Keys.streams
import spray.json.*
import spray.json.DefaultJsonProtocol.{RootJsArrayFormat, StringJsonFormat}

import java.net.URLEncoder
import java.nio.file.Path
import java.util.regex.Pattern

class SearchPluginId(
  ideaRoot: Path,
  buildInfo: BuildInfo,
  useBundled: Boolean = true,
  useRemote: Boolean = true
) {

  private val context: InstallContext = InstallContext(baseDirectory = ideaRoot, downloadDirectory = ideaRoot)

  private def getMarketplaceSearchUrl(query: String, build: String) =
    "https://plugins.jetbrains.com/api/search/plugins?search=%s&build=%s".format(query, build)

  // true if plugin was found in the remote repo
  def apply(query: String): Map[String, (String, Boolean)] = {
    val local  = if (useBundled) searchPluginIdLocal(query) else Map.empty
    val remote = if (useRemote) searchPluginIdRemote(query) else Map.empty
    remote ++ local
  }

  private def searchPluginIdLocal(query: String): Map[String, (String, Boolean)] = {
    val pattern = Pattern.compile(query)
    val registry = new LocalPluginRegistry(context)
    val allDescriptors = registry.getAllDescriptors
    allDescriptors
        .filter(descriptor => pattern.matcher(descriptor.name).find() || pattern.matcher(descriptor.id).find())
        .map(descriptor => descriptor.id -> (descriptor.name, false))
        .toMap
  }

  private def searchPluginIdRemote(queryRaw: String): Map[String, (String, Boolean)] = {
    try {
      val query: String = URLEncoder.encode(queryRaw, "UTF-8")
      val build: String = s"${buildInfo.edition.edition}-${context.productInfo.buildNumber}"
      val url: String = getMarketplaceSearchUrl(query, build)
      val data: String = getHttpGetResponseString(url)

      val jsonAst = data.parseJson
      val jsonArray = jsonAst.convertTo[JsArray]
      val values = jsonArray.elements.map(_.asJsObject())
      val names = values.map(jsObject => jsObject.fields("name").convertTo[String] -> true)
      val ids = values.map(jsObject => jsObject.fields("xmlId").convertTo[String])
      ids.zip(names).toMap
    } catch {
      case ex: Throwable =>
        PluginLogger.warn(s"Failed to query IJ plugin repo: $ex")
        Map.empty
    }
  }

  private def getHttpGetResponseString(url: String): String = {
    HttpClients.createDefault.execute(new HttpGet(url), (response: ClassicHttpResponse) => {
      EntityUtils.toString(response.getEntity, "UTF-8")
    })
  }
}

object SearchPluginId extends SbtIdeaInputTask[Map[String, (String, Boolean)]] {
  import sbt.*
  override def createTask: Def.Initialize[InputTask[Map[String, (String, Boolean)]]] = Def.inputTask {
    import complete.DefaultParsers.*
    val log = streams.value.log
    val parsed = spaceDelimited("[--nobundled|--noremote] <plugin name regexp>").parsed
    val maybeQuery = parsed.lastOption.filterNot(_.startsWith("--"))
    PluginLogger.bind(new SbtPluginLogger(streams.value))
    val result: Map[String, (String, Boolean)] = maybeQuery match {
      case Some(query) =>
        val searcher = new SearchPluginId(
          intellijBaseDirectory.value.toPath,
          BuildInfo(
            intellijBuild.value,
            intellijPlatform.value
          ),
          useBundled = !parsed.contains("--nobundled"),
          useRemote  = !parsed.contains("--noremote")
        )
        searcher(query)
      case None =>
        log.error(s"search query expected")
        Map.empty
    }
    result.foreach {
      case (id, (name, false)) => log.info(s"bundled\t\t- $name[$id]")
      case (id, (name, true)) => log.info(s"from repo\t- $name[$id]")
    }
    result
  }
}
