package org.jetbrains.sbtidea

import java.net.URL
import java.util.regex.Pattern

trait Defns { this: Keys.type =>

  sealed trait IntellijPlugin

  object IntellijPlugin {
    final case class Url(url: URL) extends IntellijPlugin
      { override def toString: String = url.toString }
    final case class Id(id: String, version: Option[String], channel: Option[String]) extends IntellijPlugin
     { override def toString: String = id }

    val URL_PATTERN: Pattern = Pattern.compile("^(?:(\\w+):)??(https?://.+)$")
    val ID_PATTERN:  Pattern = Pattern.compile("^([\\w.]+):?([\\d.]+)?:?([\\w]+)?$")

    def isExternalPluginStr(str: String): Boolean =
      str.contains(":") || ID_PATTERN.matcher(str).matches() || URL_PATTERN.matcher(str).matches()
  }

  implicit class String2Plugin(str: String) {
    import IntellijPlugin._
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
  }

  sealed trait IntelliJPlatform {
    val name: String
    def edition: String = name.takeRight(2)
    def platformPrefix: String
  }

  object IntelliJPlatform {
    object IdeaCommunity extends IntelliJPlatform {
      override val name = "ideaIC"
      override def platformPrefix: String = "Idea"
    }

    object IdeaUltimate extends IntelliJPlatform {
      override val name = "ideaIU"
      override def platformPrefix: String = ""
    }

    object PyCharmCommunity extends IntelliJPlatform {
      override val name: String = "pycharmPC"
      override def platformPrefix: String = "PyCharmCore"
    }

    object PyCharmProfessional extends IntelliJPlatform {
      override val name: String = "pycharmPY"
      override def platformPrefix: String = "Python"
    }

    object CLion extends IntelliJPlatform {
      override val name: String = "clion"
      override def edition: String = name
      override def platformPrefix: String = "CLion"
    }

    object MPS extends IntelliJPlatform {
      override val name: String = "mps"
      override def edition: String = name
      override def platformPrefix: String = ""
    }
  }

}
