package org.jetbrains.sbtidea.download.jbr

import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.{ConsoleLogger, TmpDirUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.*

import java.nio.file.{Files, Path}

class JbrInstallerTest extends AnyFunSuite with Matchers with IdeaMock with TmpDirUtils with ConsoleLogger {

  private val jbrFileName = "jbr-11_0_5-linux-x64-b520.38.tar.gz"
  private val jbrMock = s"/org/jetbrains/sbtidea/download/$jbrFileName"

  private def getMockJbrCopy: Path = {
    val tmpDir = Files.createTempDirectory(getClass.getSimpleName)
    val target = tmpDir / jbrFileName
    Files.copy(getClass.getResourceAsStream(jbrMock), target)
    target
  }

  test("detect jbr is not installed") {
    val ideaRoot = installIdeaMock
    implicit val ctx: InstallContext = InstallContext(ideaRoot, ideaRoot / "downloads")
    val jbrArtifact = JbrArtifact(JbrDependency.apply(ideaRoot,IDEA_BUILDINFO, JBR_INFO), new URL("file:"))
    val installer = new JbrInstaller
    installer.isInstalled(jbrArtifact) shouldBe false
  }

  test("detect jbr is installed") {
    val ideaRoot = installIdeaMock
    Files.createDirectory(ideaRoot / "jbr")
    implicit val ctx: InstallContext = InstallContext(ideaRoot, ideaRoot / "downloads")
    val jbrArtifact = JbrArtifact(JbrDependency.apply(ideaRoot,IDEA_BUILDINFO, JBR_INFO), new URL("file:"))
    val installer = new JbrInstaller
    installer.isInstalled(jbrArtifact) shouldBe true
  }

  test("jbr is installed") {
    val ideaRoot = installIdeaMock
    implicit val ctx: InstallContext = InstallContext(ideaRoot, ideaRoot / "downloads")
    val installer = new JbrInstaller
    installer.install(getMockJbrCopy)
    ideaRoot.toFile.list should contain ("jbr")
    (ideaRoot / "jbr").toFile.list should contain allElementsOf Seq("lib", "bin", "conf", "include", "legal", "release")
  }

}
