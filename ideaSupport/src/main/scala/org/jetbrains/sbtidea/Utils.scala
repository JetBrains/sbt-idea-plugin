package org.jetbrains.sbtidea

import java.net.URL

trait Utils {
  implicit class String2Plugin(str: String) {
    import org.jetbrains.sbtidea.IntellijPlugin._
    def toPlugin: IntellijPlugin = {
      val idMatcher  = ID_PATTERN.matcher(str)
      val urlMatcher = URL_PATTERN.matcher(str)
      if (idMatcher.find()) {
        val id = idMatcher.group(1)
        val version = Option(idMatcher.group(2))
        val channel = Option(idMatcher.group(3))
        IntellijPlugin.Id(id, version, channel)
      } else if (urlMatcher.find()) {
        val name = Option(urlMatcher.group(1)).getOrElse("")
        val url  = urlMatcher.group(2)
        Url(new URL(url))
      } else {
        throw new RuntimeException(s"Failed to parse plugin: $str")
      }
    }
    def toPlugin(excludedIds: Set[String] = Set.empty, transitive: Boolean = true, optionalDeps: Boolean = true): IntellijPlugin = {
      val res = toPlugin
      val newSettings = Settings(transitive, optionalDeps, excludedIds)
      res.resolveSettings = newSettings
      res
    }
  }
}
