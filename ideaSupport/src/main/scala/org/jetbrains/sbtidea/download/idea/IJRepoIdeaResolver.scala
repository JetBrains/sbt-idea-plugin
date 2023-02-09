package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.{Keys, PluginLogger as log}
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.Resolver
import sbt.URL

class IJRepoIdeaResolver extends Resolver[IdeaDependency] {
  private val LoggerName = this.getClass.getSimpleName

  override def resolve(dep: IdeaDependency): Seq[IdeaArtifact] = {
    val ideaUrl           = () => getUrl(dep.buildInfo, ".zip")
    // sources are available only for Community Edition
    val srcJarUrl         = () => getUrl(dep.buildInfo.copy(edition = Keys.IntelliJPlatform.IdeaCommunity), "-sources.jar")
    IdeaDistImpl(dep, ideaUrl) ::
      IdeaSourcesImpl(dep, srcJarUrl) :: Nil
  }

  private def getUrl(platform: BuildInfo, artifactSuffix: String): URL = {
    val locationDescriptor = IntellijVersionUtils.detectArtifactLocation(platform, artifactSuffix)
    val artifactVersion = locationDescriptor.artifactVersion
    val artifactUrl = locationDescriptor.url
    log.warn(s"""[$LoggerName] Artifact location for build number ${platform.buildNumber}: version: $artifactVersion, url: $artifactUrl""")
    artifactUrl
  }
}