package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.{CapturingLogger, PluginLogger, TmpDirUtils}
import org.scalatest.{FunSuite, Matchers}

final class IdeaUpdaterTest extends FunSuite with Matchers with TmpDirUtils with IdeaMock {

  import IdeaUpdater._

  test("dumb mode settings are honored") {
    val capturingLogger = new CapturingLogger
    val tmpDir = newTmpDir
    tmpDir.toFile.deleteOnExit()
    val updater = new IdeaUpdater(null, null, tmpDir, capturingLogger)
    try {
      sys.props += DUMB_KEY -> s"$DUMB_KEY_IDEA|$DUMB_KEY_PLUGINS"
      updater.updateIdeaAndPlugins(null, null)
      capturingLogger.messages shouldBe empty
    } finally {
      sys.props -= DUMB_KEY
    }
  }

  test("IJ repo overriding property") {
    val capturingLogger = new CapturingLogger
    try {
      sys.props += IJ_REPO_OVERRIDE -> s"https://127.0.0.1:/non-existing-path"
      val resolver = new JBIdeaRepoArtifactResolver { override protected def log: PluginLogger = capturingLogger }

      assertThrows[java.net.ConnectException](resolver.resolveUrlForIdeaBuild(IDEA_BUILDINFO))
      capturingLogger.messages should contain ("Using non-default IntelliJ repository URL: https://127.0.0.1:/non-existing-path")
    } finally {
      sys.props -= IJ_REPO_OVERRIDE
    }
  }

}
