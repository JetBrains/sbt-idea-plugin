package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.CapturingLogger.captureLog
import org.jetbrains.sbtidea.download.IdeaUpdater.{DUMB_KEY, DUMB_KEY_IDEA, DUMB_KEY_JBR, DUMB_KEY_PLUGINS}
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.{PathExt, TmpDirUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.*

class IdeaInstallerTest extends AnyFunSuite with Matchers with IdeaMock with TmpDirUtils {

  private def createInstaller = new IdeaDistInstaller(IDEA_BUILDINFO)

  private implicit val installContext: InstallContext = {
    val tmpDir = newTmpDir
    InstallContext(tmpDir / IDEA_BUILDINFO.buildNumber, tmpDir)
  }

  test("dumb mode settings are honored") {
    val tmpDir = newTmpDir
    tmpDir.toFile.deleteOnExit()
    val updater = createInstaller
    try {
      sys.props += DUMB_KEY -> s"$DUMB_KEY_IDEA|$DUMB_KEY_PLUGINS|$DUMB_KEY_JBR"
      val messages = captureLog { updater.isInstalled(IDEA_ART) shouldBe true }
      messages shouldBe empty
    } finally {
      sys.props -= DUMB_KEY
    }
  }

  test("IdeaInstaller installs IDEA dist") {
    val installer = createInstaller
    val dist = getDistCopy
    val ideaInstallRoot = installer.installDist(dist)
    ideaInstallRoot.toFile.exists() shouldBe true

    val fileNamesInRoot = ideaInstallRoot.list.map(_.getFileName.toString).toList
    fileNamesInRoot should contain allElementsOf Seq(
      "lib",
      "bin",
      "plugins",
      "product-info.json",
      "build.txt",
      "dependencies.txt"
    )
  }
}
