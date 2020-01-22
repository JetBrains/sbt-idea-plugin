package org.jetbrains.sbtidea.download

import java.nio.file.Path

import org.jetbrains.sbtidea.{ConsoleLogger, Keys, TmpDirUtils}
import org.scalatest.{FunSuite, Matchers}
import org.jetbrains.sbtidea.pathToPathExt

class IdeaInstallerTest extends FunSuite with Matchers with IdeaMock with TmpDirUtils with ConsoleLogger {

  private def createInstaller: CommunityIdeaInstaller = new CommunityIdeaInstaller(newTmpDir, IDEA_BUILDINFO, log) {
    override def isPluginAlreadyInstalledAndUpdated(plugin: Keys.IntellijPlugin): Boolean = true
    override def installIdeaPlugin(plugin: Keys.IntellijPlugin, file: Path): Path = null
  }

  test("IdeaInstaller installs IDEA dist") {
    val installer = createInstaller
    val dist = getDistCopy
    val parts = ArtifactPart(dist.toUri.toURL, ArtifactKind.IDEA_DIST) -> dist
    val ideaInstallRoot = installer.installIdeaDist(Seq(parts))
    ideaInstallRoot.toFile.exists() shouldBe true
    ideaInstallRoot.list.map(_.getFileName.toString) should contain allElementsOf Seq("lib", "bin", "plugins", "build.txt")
  }

}
