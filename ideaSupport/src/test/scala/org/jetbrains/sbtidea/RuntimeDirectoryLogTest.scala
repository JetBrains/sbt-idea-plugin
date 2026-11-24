package org.jetbrains.sbtidea

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RuntimeDirectoryLogTest extends AnyFunSuite with Matchers {
  test("report only directory properties, using the final assignment and preserving spaces and literal quotes") {
    val output = CapturingLogger.captureLog {
      RuntimeDirectoryLog.report(RuntimePurpose.Tests, "my-plugin", Seq(
        "-Didea.system.path=old", "-Dprivate.token=do-not-log",
        "-Didea.config.path=\"/path with spaces/config\"",
        "-Didea.system.path=/path with spaces/system=custom",
        "-Didea.log.path=/different logs",
      ), PluginLogger)
    }
    output shouldBe Seq(
      "[info] IntelliJ runtime (tests, my-plugin)",
      "[info]   System: /path with spaces/system=custom",
      "[info]   Config: \"/path with spaces/config\"",
      "[info]   Logs: /different logs",
    )
  }

  test("missing properties are explicit and a non-IntelliJ fork remains quiet") {
    CapturingLogger.captureLog {
      RuntimeDirectoryLog.report(RuntimePurpose.Tests, "root", Seq("-Xmx512m"), PluginLogger)
    } shouldBe empty
    val output = CapturingLogger.captureLog {
      RuntimeDirectoryLog.report(RuntimePurpose.Tests, "root", Seq("-Didea.system.path=/system"), PluginLogger)
    }
    output should contain("[info]   Config: not set (IDE default)")
    output should contain("[info]   Logs: not set (IDE default)")
  }
}
