package org.jetbrains.sbtidea.download.plugin

import java.nio.file.Path

import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.ConsoleLogger
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.jbr.JbrDependency
import org.scalatest.{FunSuite, Matchers}
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

import scala.language.implicitConversions


trait IntellijPluginInstallerTestBase extends FunSuite with Matchers with IdeaMock with PluginMock with ConsoleLogger {
  protected lazy val ideaRoot: Path   = installIdeaMock
  protected val pluginsRoot: Path     = ideaRoot / "plugins"
  protected val ideaBuild: BuildInfo  = BuildInfo(IDEA_VERSION, IntelliJPlatform.IdeaUltimate, Some(JbrDependency.VERSION_AUTO))

  protected implicit val defaultBuildInfo: BuildInfo = IDEA_BUILDINFO

  protected implicit val localRegistry: LocalPluginRegistryApi = new LocalPluginRegistry(ideaRoot)
  protected implicit val repoAPI: PluginRepoApi = new PluginRepoUtils

  protected def createInstaller(implicit buildInfo: BuildInfo = IDEA_BUILDINFO): RepoPluginInstaller =
    new RepoPluginInstaller(buildInfo)

  protected implicit def plugin2PluginDep(pl: IntellijPlugin)(implicit buildInfo: BuildInfo): PluginDependency =
    PluginDependency(pl, buildInfo)

  protected implicit def plugin2PluginArt(pl: IntellijPlugin): RemotePluginArtifact =
    RemotePluginArtifact(pl, new URL("file:"))

  protected implicit def installContext: InstallContext = InstallContext(ideaRoot, ideaRoot.getParent)
}
