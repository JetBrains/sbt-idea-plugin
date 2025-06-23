package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.PluginLogger as log
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.IdeaUpdater.IJ_REPO_OVERRIDE
import sbt.MavenRepository

import java.net.URL

object IntellijRepositories {
  private val LoggerName = this.getClass.getSimpleName.stripSuffix("$")

  private val BaseIntelliJRepositoryUrl = {
    val urlFormEnv = System.getProperty(IJ_REPO_OVERRIDE)
    if (urlFormEnv != null) {
      log.warn(s"[$LoggerName] Using non-default IntelliJ repository URL: $urlFormEnv")
      urlFormEnv
    } else {
      "https://cache-redirector.jetbrains.com/intellij-repository"
    }
  }

  val Releases: MavenRepository = MavenRepository("intellij-repository-releases", s"$BaseIntelliJRepositoryUrl/releases")
  val Eap: MavenRepository = MavenRepository("intellij-repository-eap", s"$BaseIntelliJRepositoryUrl/snapshots")
  val Nightly: MavenRepository = MavenRepository("intellij-repository-nightly", s"$BaseIntelliJRepositoryUrl/nightly")

  /**
   * !!! ATTENTION !!<br>
   * Can access internet to calculate artifact location using [[org.jetbrains.sbtidea.download.idea.IntellijVersionUtils.detectArtifactLocation]]
   */
  def getArtifactUrl(platform: BuildInfo, artifactSuffix: String): URL = {
    val locationDescriptor = IntellijVersionUtils.detectArtifactLocation(platform, artifactSuffix)
    val artifactVersion = locationDescriptor.artifactVersion
    val artifactUrl = locationDescriptor.url
    log.warn(s"""[$LoggerName] Artifact location for build number ${platform.buildNumber}: version: $artifactVersion, url: $artifactUrl""")
    artifactUrl
  }
}