package org.jetbrains.sbtidea

import java.net.URL
import java.util.regex.Pattern

sealed trait IntellijPlugin {
    var resolveSettings: IntellijPlugin.Settings = IntellijPlugin.defaultSettings
}

object IntellijPlugin {
  final case class Url(url: URL) extends IntellijPlugin
    { override def toString: String = url.toString }
  final case class Id(id: String, version: Option[String], channel: Option[String])(val url: Option[URL] = None) extends IntellijPlugin
   { override def toString: String = id }
  final case class BundledFolder(name: String) extends IntellijPlugin

  val URL_PATTERN: Pattern = Pattern.compile("^(?:(\\w+):)??(https?://.+)$")
  val ID_PATTERN:  Pattern = Pattern.compile("^([^:]+):?([\\w.]+)?:?([\\w]+)?$")
  val ID_WITH_URL: Pattern = Pattern.compile("^([^:]+):?([\\w.-]+)?:?(https?://.+)?$")

  case class Settings(transitive: Boolean = true, optionalDeps: Boolean = true, excludedIds: Set[String] = Set.empty)
  val defaultSettings: Settings = Settings()

  def isExternalPluginStr(str: String): Boolean =
    str.contains(":") || ID_PATTERN.matcher(str).matches() || URL_PATTERN.matcher(str).matches()
}