package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.{CapturingLogger, TmpDirUtils}
import org.scalatest.{FunSuite, Matchers}

final class IdeaUpdaterTest extends FunSuite with Matchers with TmpDirUtils {

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

}
