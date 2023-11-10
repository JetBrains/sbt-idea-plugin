package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.IntellijPlugin.Settings

import java.net.URL

sealed trait IntellijPlugin {
  var resolveSettings: Settings = Settings.Default
}

object IntellijPlugin {

  final case class BundledFolder(name: String) extends IntellijPlugin

  //noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  sealed trait WithKnownId extends IntellijPlugin {
    def id: String

    override def toString: String = id

    //keeping the method here in order it's more convenient to use it like this:
    //"org.intellij.plugins.markdown".toPlugin.withFallbackDownloadUrl(...)
    def withFallbackDownloadUrl(url: Option[URL]): WithKnownId =
      throw new UnsupportedOperationException("Only supported for IntellijPlugin.Id")

    final def withFallbackDownloadUrl(url: String): WithKnownId =
      withFallbackDownloadUrl(Some(url))

    final def withFallbackDownloadUrl(url: Option[String])(implicit d: DummyImplicit): WithKnownId =
      withFallbackDownloadUrl(url.map(new URL(_)))
  }

  /**
   * @param fallbackDownloadUrl the url will be used if the plugin can't be resolved in Marketplace
   */
  final case class Id(
    override val id: String,
    version: Option[String],
    channel: Option[String],
    fallbackDownloadUrl: Option[URL] = None
  ) extends WithKnownId {
    override def withFallbackDownloadUrl(url: Option[URL]): WithKnownId =
      this.copy(fallbackDownloadUrl = url)
  }

  final case class IdWithDownloadUrl(
    override val id: String,
    downloadUrl: URL
  ) extends WithKnownId

  case class Settings(
    transitive: Boolean = true,
    optionalDeps: Boolean = true,
    excludedIds: Set[String] = Set.empty,
  )

  object Settings {
    val Default: Settings = Settings()
  }

  private[sbtidea] implicit class IntellijPluginOps(private val target: IntellijPlugin) extends AnyVal {
    def fallbackDownloadUrl: Option[URL] = target match {
      case id: Id => id.fallbackDownloadUrl
      case _ => None
    }
  }
}