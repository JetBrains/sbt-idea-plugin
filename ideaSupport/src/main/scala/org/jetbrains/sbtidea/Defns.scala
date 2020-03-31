package org.jetbrains.sbtidea

import java.net.URL
import java.util.regex.Pattern

trait Defns { this: Keys.type =>

  sealed trait IntellijPlugin {
    var transitive = true
    var optionalDeps = true
    var excludedIds: Set[String] = Set.empty
  }

  object IntellijPlugin {
    final case class Url(url: URL) extends IntellijPlugin
      { override def toString: String = url.toString }
    final case class Id(id: String, version: Option[String], channel: Option[String]) extends IntellijPlugin
     { override def toString: String = id }
    final case class BundledFolder(name: String) extends IntellijPlugin

    val URL_PATTERN: Pattern = Pattern.compile("^(?:(\\w+):)??(https?://.+)$")
    val ID_PATTERN:  Pattern = Pattern.compile("^([^:]+):?([\\w.]+)?:?([\\w]+)?$")

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
    def toPlugin(excludedIds: Set[String] = Set.empty, transitive: Boolean = true, optionalDeps: Boolean = true): IntellijPlugin = {
      val res = toPlugin
      res.excludedIds = excludedIds
      res.optionalDeps = optionalDeps
      res.transitive = transitive
      res
    }
  }

  class pluginXmlOptions {
    var version: String = _
    var sinceBuild: String = _
    var untilBuild: String = _
    var pluginDescription: String = _
    var changeNotes: String = _
    def apply(func: pluginXmlOptions => Unit): pluginXmlOptions = { func(this); this }
  }

  object pluginXmlOptions {
    val DISABLED = new pluginXmlOptions()
    def apply(init: pluginXmlOptions => Unit): pluginXmlOptions = {
      val xml = new pluginXmlOptions()
      init(xml)
      xml
    }
  }

  sealed trait IntelliJPlatform {
    val name: String
    def edition: String = name.takeRight(2)
    def platformPrefix: String
    override def toString: String = name
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
