package org.jetbrains.sbtidea

import java.net.URL
import java.util.regex.Pattern

trait Defns { this: Keys.type =>

  sealed trait IdeaPlugin

  object IdeaPlugin {
    final case class Url(url: URL) extends IdeaPlugin
      { override def toString: String = url.toString }
    final case class Id(id: String, version: Option[String], channel: Option[String]) extends IdeaPlugin
     { override def toString: String = id }

    val URL_PATTERN: Pattern = Pattern.compile("^(?:(\\w+):)??(https?://.+)$")
    val ID_PATTERN:  Pattern = Pattern.compile("^([\\w.]+):?([\\d.]+)?:?([\\w]+)?$")

    def isExternalPluginStr(str: String): Boolean =
      str.contains(":") || ID_PATTERN.matcher(str).matches() || URL_PATTERN.matcher(str).matches()
  }

  implicit class String2Plugin(str: String) {
    import IdeaPlugin._
    def toPlugin: IdeaPlugin = {
      val idMatcher  = ID_PATTERN.matcher(str)
      val urlMatcher = URL_PATTERN.matcher(str)
      if (idMatcher.find()) {
        val id = idMatcher.group(1)
        val version = Option(idMatcher.group(2))
        val channel = Option(idMatcher.group(3))
        IdeaPlugin.Id(id, version, channel)
      } else if (urlMatcher.find()) {
        val name = Option(urlMatcher.group(1)).getOrElse("")
        val url  = urlMatcher.group(2)
        Url(new URL(url))
      } else {
        throw new RuntimeException(s"Failed to parse plugin: $str")
      }
    }
  }

  sealed trait IdeaEdition {
    val name: String
    def shortname: String = name.takeRight(2)
  }

  object IdeaEdition {
    object Community extends IdeaEdition {
      override val name = "ideaIC"
    }

    object Ultimate extends IdeaEdition {
      override val name = "ideaIU"
    }
  }

  final case class PublishSettings(pluginId: String, username: String, password: String, channel: Option[String])

}
