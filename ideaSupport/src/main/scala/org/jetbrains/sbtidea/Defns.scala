package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.download.jbr.JbrBintrayResolver

import java.net.URL
import java.util.Locale
import java.util.regex.Pattern

trait Defns { this: Keys.type =>

  sealed trait IntellijPlugin {
    var resolveSettings: IntellijPlugin.Settings = IntellijPlugin.defaultSettings
  }

  object IntellijPlugin {
    final case class Url(url: URL) extends IntellijPlugin
      { override def toString: String = url.toString }
    final case class Id(id: String, version: Option[String], channel: Option[String]) extends IntellijPlugin
     { override def toString: String = id }
    final case class BundledFolder(name: String) extends IntellijPlugin

    val URL_PATTERN: Pattern = Pattern.compile("^(?:(\\w+):)??(https?://.+)$")
    val ID_PATTERN:  Pattern = Pattern.compile("^([^:]+):?([\\w.]+)?:?([\\w]+)?$")

    case class Settings(transitive: Boolean = true, optionalDeps: Boolean = true, excludedIds: Set[String] = Set.empty)
    val defaultSettings: Settings = Settings()

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
      val newSettings = Settings(transitive, optionalDeps, excludedIds)
      res.resolveSettings = newSettings
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

  sealed trait JbrInfo {
    def major: String
    def minor: String
    def kind: String
    def platform: String
    def arch: String
  }
  case class  JBR(major: String, minor: String, kind: String, platform: String, arch: String) extends JbrInfo
  trait AutoJbrPlatform {
    def platform: String = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH) match {
      case value if value.startsWith("win") => "windows"
      case value if value.startsWith("lin") => "linux"
      case value if value.startsWith("mac") => "osx"
      case other => throw new IllegalStateException(s"OS $other is unsupported")
    }
    def arch: String = System.getProperty("os.arch") match {
      case "x86"  => "x86"
      case _      => "x64"
    }
  }
  trait DynamicJbrInfo {
    def major: String = throw new IllegalStateException("Static evaluation of JBR major version is unsupported in AutoJbr")
    def minor: String = throw new IllegalStateException("Static evaluation of JBR minor version is unsupported in AutoJbr")
    def kind: String  = throw new IllegalStateException("Static evaluation of JBR kind is unsupported in AutoJbr")
  }
  case class AutoJbr() extends JbrInfo with AutoJbrPlatform with DynamicJbrInfo
  case class AutoJbrWithPlatform(major: String, minor: String, kind: String = JbrBintrayResolver.JDR_DCEVM_KIND) extends JbrInfo with AutoJbrPlatform
  case class AutoJbrWithKind(override val kind: String) extends JbrInfo with AutoJbrPlatform with DynamicJbrInfo


  case class IdeaConfigBuildingOptions(generateDefaultRunConfig: Boolean = true,
                                       generateJUnitTemplate: Boolean = true,
                                       generateNoPCEConfiguration: Boolean = false,
                                       programParams: String = "",
                                       ideaRunEnv: Map[String, String] = Map.empty,
                                       ideaTestEnv: Map[String, String] = Map.empty,
                                       testModuleName: String = "",
                                       workingDir: String = "$PROJECT_DIR$/",
                                       testSearchScope: String = "moduleWithDependencies")

}
