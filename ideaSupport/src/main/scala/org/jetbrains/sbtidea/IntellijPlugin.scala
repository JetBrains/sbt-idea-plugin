package org.jetbrains.sbtidea

import java.net.URL

sealed trait IntellijPlugin {
  var resolveSettings: IntellijPlugin.Settings = IntellijPlugin.Settings()
}

object IntellijPlugin {
  /**
   * @param name not used but might be a helpful readable reminder
   */
  final case class Url(name: Option[String], url: URL) extends IntellijPlugin {
    override def toString: String = url.toString
  }

  sealed trait WithKnownId extends IntellijPlugin {
    def id: String
  }

  final case class Id(override val id: String, version: Option[String], channel: Option[String]) extends WithKnownId {
    override def toString: String = id
  }

  final case class IdWithCustomUrl(override val id: String, downloadUrl: URL) extends WithKnownId {
    override def toString: String = id
  }

  final case class BundledFolder(name: String) extends IntellijPlugin

  case class Settings(
    transitive: Boolean = true,
    optionalDeps: Boolean = true,
    excludedIds: Set[String] = Set.empty
  )
}