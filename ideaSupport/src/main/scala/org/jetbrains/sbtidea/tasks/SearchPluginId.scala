package org.jetbrains.sbtidea.tasks

import java.net.URLEncoder
import java.nio.file.Path
import java.util.regex.Pattern

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.plugin.LocalPluginRegistry
import scalaj.http.Http

class SearchPluginId(ideaRoot: Path, buildInfo: BuildInfo, useBundled: Boolean = true, useRemote: Boolean = true) {

  private val REPO_QUERY = "https://plugins.jetbrains.com/api/search/plugins?query=%s&build=%s"

  // true if plugin was found in the remote repo
  def apply(query: String): Map[String, (String, Boolean)] = {
    val local  = if (useBundled) searchPluginIdLocal(query) else Map.empty
    val remote = if (useRemote) searchPluginIdRemote(query) else Map.empty
    local ++ remote
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
  // FIXME: use java json parsing library to workaround this API nonsense
  private def searchPluginIdRemote(query: String): Map[String, (String, Boolean)] = {
    Map.empty
  }
}
