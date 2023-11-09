package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.PluginLogger as log
import org.jetbrains.sbtidea.Utils.*

import java.net.URL
import scala.util.matching.Regex

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

object Utils {
  /**
   * id:[version]:[channel]
   *
   * Examples:
   *  - plugin-id
   *  - plugin-id:2023.3.1
   *  - plugin-id:2023.3.1:eap
   */
  private val IdRegex: Regex = "^([^:]+):?([\\w.-]+)?:?(\\w+)?$".r

  /**
   * id:[channel]:url
   *
   * Examples:
   *  - plugin-id:https://org.example
   *  - plugin-id:2023.3.1:https://org.example //!!! version is not actually used, but it's parsed not to break old usages
   */
  private val IdWithCustomUrlRegex: Regex = "^([^:]+):?([\\w.-]+)?:?(https?://.+)$".r
}
