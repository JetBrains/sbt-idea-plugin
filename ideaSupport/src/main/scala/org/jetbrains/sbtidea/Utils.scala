package org.jetbrains.sbtidea

import java.net.URL

trait Utils {

  implicit class String2Plugin(str: String) {

    import org.jetbrains.sbtidea.IntellijPlugin.*

    def toPlugin: IntellijPlugin = {
      str match {
        case IdRegex(id, version, channel) =>
          IntellijPlugin.Id(id, Option(version), Option(channel))
        case IdWithCustomUrlRegex(id, version, url) =>
          IntellijPlugin.IdWithCustomUrl(id, Option(version), new URL(url))
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
