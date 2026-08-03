package org.jetbrains.sbtidea

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt.ForkOptions

import java.nio.file.Paths

class IntellijTestRuntimeDirectoriesTest extends AnyFunSuite with Matchers {
  private val ideaHome = Paths.get("idea home")
  private val directories = IntellijTestRuntimeDirectories(ideaHome)

  test("uses an isolated IDEA system, config, and log directory for a runtime ID") {
    directories.forRuntimeId("tc-123_outer-5.inner-1").jvmOptions shouldBe Vector(
      "-Didea.system.path=idea home/test-system-tc-123_outer-5.inner-1",
      "-Didea.config.path=idea home/test-config-tc-123_outer-5.inner-1",
      "-Didea.log.path=idea home/test-system-tc-123_outer-5.inner-1/log",
    )
  }

  test("replaces only IntelliJ test directory options in fork options") {
    val initialJvmOptions: Vector[String] = Vector(
      "-Xmx2g",
      "-Didea.system.path=shared-system",
      "-Didea.config.path=shared-config",
      "-Didea.log.path=shared-log",
      "-Didea.plugins.path=shared-plugins",
      "-Dtest.option=value",
    )
    val forkOptions = ForkOptions().withRunJVMOptions(initialJvmOptions)

    val isolated = directories.applyTo(forkOptions, "run-42")

    isolated.runJVMOptions shouldBe Vector(
      "-Xmx2g",
      "-Didea.plugins.path=shared-plugins",
      "-Dtest.option=value",
      "-Didea.system.path=idea home/test-system-run-42",
      "-Didea.config.path=idea home/test-config-run-42",
      "-Didea.log.path=idea home/test-system-run-42/log",
    )
  }

  test("rejects unsafe runtime IDs") {
    Seq("", ".", "../other", "outer/inner", "has space").foreach { runtimeId =>
      an[IllegalArgumentException] should be thrownBy directories.forRuntimeId(runtimeId)
    }
  }
}
