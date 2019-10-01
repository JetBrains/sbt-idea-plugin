package org.jetbrains.sbtidea.download


import java.nio.file._

import org.jetbrains.sbtidea.Keys.IntelliJPlatform
import org.jetbrains.sbtidea.{ConsoleLogger, PluginLogger}
import org.scalatest.{FunSuite, Matchers}

trait IntellijPluginInstallerTestBase extends FunSuite with Matchers with IdeaMock with PluginMock with ConsoleLogger {
  protected lazy val ideaRoot: Path   = installIdeaMock
  protected val pluginsRoot: Path     = ideaRoot / "plugins"
  protected val ideaBuild: BuildInfo  = BuildInfo(IDEA_VERSION, IntelliJPlatform.IdeaUltimate)

  protected def createInstaller(logger: PluginLogger = log): IdeaPluginInstaller = new IdeaPluginInstaller {
    override protected def buildInfo: BuildInfo = ideaBuild
    override protected def log: PluginLogger = logger
    override def getInstallDir: Path = ideaRoot
    override def isIdeaAlreadyInstalled: Boolean = true
    override def installIdeaDist(files: Seq[(ArtifactPart, Path)]): Path = ideaRoot
  }
}
