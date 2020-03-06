package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.CapturingLogger.captureLog
import org.jetbrains.sbtidea.{ConsoleLogger, TmpDirUtils}
import org.scalatest.{FunSuite, Matchers}


class IJRepoResolverTest extends FunSuite with Matchers with IdeaMock with TmpDirUtils with ConsoleLogger {

  import org.jetbrains.sbtidea.download.IdeaUpdater._

  test("IJ repo overriding property") {
    try {
      sys.props += IJ_REPO_OVERRIDE -> s"https://127.0.0.1:/non-existing-path"
      val resolver = new IJRepoIdeaResolver

      val messages = captureLog(
        assertThrows[java.net.ConnectException](resolver.resolve(IDEA_DEP))
      )

      messages should contain("Using non-default IntelliJ repository URL: https://127.0.0.1:/non-existing-path")
    } finally {
      sys.props -= IJ_REPO_OVERRIDE
    }
  }
}
