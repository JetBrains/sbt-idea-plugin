package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.PluginLogger as log

import java.net.URL

trait Utils {

  implicit class String2Plugin(str: String) {

    import org.jetbrains.sbtidea.IntellijPlugin.*

    def toPlugin: IntellijPlugin = {
      str match {
        case IdRegex(id, version, channel) =>
          IntellijPlugin.Id(id, Option(version), Option(channel))
        case IdWithCustomUrlRegex(id, version, url) =>
          if (version != null) {
            log.warn(s"Version `$version` in plugin reference `$id` is not used because a direct link is used to download the plugin: $url")
          }
          IntellijPlugin.IdWithCustomUrl(id, new URL(url))
        case UrlRegex(name, url) =>
          Url(Option(name), new URL(url))
        case _ =>
          throw new RuntimeException(s"Failed to parse plugin: $str")
      }
    }

    def toPlugin(
      excludedIds: Set[String] = Set.empty,
      transitive: Boolean = true,
      optionalDeps: Boolean = true
    ): IntellijPlugin = {
      val res = toPlugin
      val newSettings = Settings(transitive, optionalDeps, excludedIds)
      res.resolveSettings = newSettings
      res
    }
  }
}
