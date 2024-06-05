package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.CapturingLogger.captureLog
import org.jetbrains.sbtidea.TmpDirUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers


class IJRepoResolverTest extends AnyFunSuite with Matchers with IdeaMock with TmpDirUtils {

  import org.jetbrains.sbtidea.download.IdeaUpdater.*

  test("IJ repo overriding property") {
    try {
      sys.props += IJ_REPO_OVERRIDE -> s"https://127.0.0.1:/non-existing-path"
      val resolver = new IJRepoIdeaResolver

      val messages = captureLog(
        resolver.resolve(IDEA_DEP).foreach(_.dlUrl)
      )

      messages should contain("[warn] [IntellijRepositories] Using non-default IntelliJ repository URL: https://127.0.0.1:/non-existing-path")
      messages should contain("[warn] [IntellijVersionUtils] Cannot detect artifact location for version 242.14146.5, fallback to: (242.14146-EAP-CANDIDATE-SNAPSHOT,intellij-repository-eap: https://127.0.0.1:/non-existing-path/snapshots)")
      messages should contain("[warn] [IntellijVersionUtils] Cannot detect artifact location for version 242.14146.5, fallback to: (242.14146-EAP-CANDIDATE-SNAPSHOT,intellij-repository-eap: https://127.0.0.1:/non-existing-path/snapshots)")
    } finally {
      sys.props -= IJ_REPO_OVERRIDE
    }
  }
}
