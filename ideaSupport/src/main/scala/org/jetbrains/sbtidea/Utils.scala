package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.IntellijPlugin.Settings
import org.jetbrains.sbtidea.PluginLogger as log

import java.net.URL
import scala.util.matching.Regex

trait Utils {

  implicit class String2Plugin(str: String) {
    def toPlugin: IntellijPlugin.WithKnownId =
      Utils.parsePlugin(str)

    def toPlugin(
      excludedIds: Set[String] = Settings.Default.excludedIds,
      transitive: Boolean = Settings.Default.transitive,
      optionalDeps: Boolean = Settings.Default.optionalDeps,
    ): IntellijPlugin.WithKnownId = {
      val res = toPlugin
      val newSettings = IntellijPlugin.Settings(transitive, optionalDeps, excludedIds)
      res.resolveSettings = newSettings
      res
    }
  }
}

object Utils {
  private def parsePlugin(str: String): IntellijPlugin.WithKnownId = str match {
    case IdRegex(id, version, channel) =>
      IntellijPlugin.Id(id, Option(version), Option(channel))
    case IdWithCustomUrlRegex(id, version, url) =>
      if (version != null) {
        log.warn(s"Version `$version` in plugin reference `$id` is not used because a direct link is used to download the plugin: $url")
      }
      IntellijPlugin.IdWithDownloadUrl(id, new URL(url))
    case _ =>
      throw new RuntimeException(
        s"""Failed to parse plugin: $str.
           |Here are some examples of valid strings:
           |${PluginStringExamples.mkString("\n")}""".stripMargin
      )
  }

  val PluginStringExamples: Seq[String] = Seq(
    """org.intellij.scala""",
    """org.intellij.scala:2023.3.6""",
    """org.intellij.scala:2023.3.6:Eap""",
    """org.intellij.scala:2023.3.6:Nightly""",
    """org.custom.plugin""",
    """org.custom.plugin:2022.1.1""",
    """org.custom.plugin:https://org.example/path/to/your/plugin.zip""",
  )

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
