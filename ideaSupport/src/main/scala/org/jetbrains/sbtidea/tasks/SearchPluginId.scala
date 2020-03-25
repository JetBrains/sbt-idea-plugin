package org.jetbrains.sbtidea.tasks

import java.net.URLEncoder
import java.nio.file.Path
import java.util.regex.Pattern

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import com.eclipsesource.json._
import scalaj.http.Http

import scala.collection.JavaConverters._

class SearchPluginId(ideaRoot: Path, buildInfo: BuildInfo, useBundled: Boolean = true, useRemote: Boolean = true) {

  private val REPO_QUERY = "https://plugins.jetbrains.com/api/search/plugins?search=%s&build=%s"

  // true if plugin was found in the remote repo
  def apply(query: String): Map[String, (String, Boolean)] = {
    val local  = if (useBundled) searchPluginIdLocal(query) else Map.empty
    val remote = if (useRemote) searchPluginIdRemote(query) else Map.empty
    remote ++ local
  }

  private def searchPluginIdLocal(query: String): Map[String, (String, Boolean)] = {
    val pattern = Pattern.compile(query)
    val registry = new LocalPluginRegistry(ideaRoot)
    val allDescriptors = registry.getAllDescriptors
    allDescriptors
        .filter(descriptor => pattern.matcher(descriptor.name).find() || pattern.matcher(descriptor.id).find())
        .map(descriptor => descriptor.id -> (descriptor.name, false))
        .toMap
  }

  // Apparently we can't use json4s when cross-compiling for sbt because there are BOTH no shared versions AND binary compatibility
  private def searchPluginIdRemote(query: String): Map[String, (String, Boolean)] = {
    try {
      val param = URLEncoder.encode(query, "UTF-8")
      val url = REPO_QUERY.format(param, s"${buildInfo.edition.edition}-${buildInfo.buildNumber}")
      val data = Http(url).asString.body
      val json = Json.parse(data)
      val values = json.asArray().values().asScala.map(_.asObject())
      val names = values.map(_.getString("name", "") -> true)
      val ids = values.map(_.getString("xmlId", ""))
      ids.zip(names).toMap
    } catch {
      case ex: Throwable =>
        PluginLogger.warn(s"Failed to query IJ plugin repo: $ex")
        Map.empty
    }
  }
}
