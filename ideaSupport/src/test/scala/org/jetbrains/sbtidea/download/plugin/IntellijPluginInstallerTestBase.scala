package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.download.{BuildInfo, NioUtils}
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.*

import java.nio.file.Path
import scala.language.implicitConversions


trait IntellijPluginInstallerTestBase extends AnyFunSuite with Matchers with IdeaMock with PluginMock with BeforeAndAfter {
  protected var ideaRoot: Path        = _
  protected var pluginsRoot: Path     = _
  protected val ideaBuild: BuildInfo  = BuildInfo(IDEA_VERSION, IntelliJPlatform.IdeaUltimate)

  protected implicit val defaultBuildInfo: BuildInfo = IDEA_BUILDINFO

  protected implicit def localRegistry: LocalPluginRegistryApi  = new LocalPluginRegistry(installContext)
  protected implicit def repoAPI: PluginRepoApi                 = new PluginRepoUtils

  protected def createInstaller(implicit buildInfo: BuildInfo = IDEA_BUILDINFO): RepoPluginInstaller =
    new RepoPluginInstaller(buildInfo)

  protected implicit def plugin2PluginDep(pl: IntellijPlugin)(implicit buildInfo: BuildInfo): PluginDependency =
    PluginDependency(pl, buildInfo)

  protected implicit def plugin2PluginArt(pl: IntellijPlugin): RemotePluginArtifact =
    RemotePluginArtifact(pl, new URL("file:"))

  protected implicit def installContext: InstallContext = InstallContext(ideaRoot, ideaRoot.getParent)

  before {
    val mockInstallation = installIdeaMock
    ideaRoot = mockInstallation
    pluginsRoot = ideaRoot / "plugins"
  }

  after {
    NioUtils.delete(ideaRoot)
  }
}
