package org.jetbrains.sbtidea.runtimeCleanup

import org.jetbrains.sbtidea.TmpDirUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

class RuntimeProcessesTest extends AnyFunSuite with Matchers with TmpDirUtils {
  test("procfs arguments preserve long command lines, spaces and empty arguments") {
    val directory = newTmpDir
    val system = directory.resolve("system with spaces").toFile.getCanonicalFile.toPath
    val arguments = Seq("-Dpadding=" + "x" * 8192, "", s"-Didea.system.path=$system", "Main")
    val file = directory.resolve("cmdline")
    Files.write(file, (Seq("/jdk/bin/java") ++ arguments).mkString("", "\u0000", "\u0000").getBytes(Charset.defaultCharset()))
    RuntimeProcesses.readProcArguments(file) shouldBe Some(arguments)
    RuntimeProcesses.readProcArguments(file).flatMap(RuntimeProcesses.pathsIn) shouldBe Some(Set(system))
  }

  test("unavailable, incomplete and oversized procfs arguments remain uninspectable") {
    val file = newTmpDir.resolve("cmdline")
    RuntimeProcesses.readProcArguments(file) shouldBe None
    Seq(Array.emptyByteArray, "java\u0000incomplete".getBytes(Charset.defaultCharset()),
      Array[Byte](0), new Array[Byte](1024 * 1024 + 1)).foreach { bytes =>
      Files.write(file, bytes)
      RuntimeProcesses.readProcArguments(file) shouldBe None
    }
  }

  Seq("within an argument" -> 8192, "between arguments" -> 4096).foreach { case (boundary, size) =>
    test(s"an actual JVM whose command line exceeds the JDK 17 Linux limit $boundary remains inspectable") {
      val directory = newTmpDir
      val ready = directory.resolve("ready")
      val system = directory.resolve("system with spaces").toFile.getCanonicalFile.toPath
      val classes = Paths.get(classOf[RuntimeProcessProbe].getProtectionDomain.getCodeSource.getLocation.toURI)
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString
      // Put the runtime option after the 4 KB boundary, including the case where
      // ProcessHandle reports a present but incomplete argument array on Linux.
      val padding = size - java.getBytes(Charset.defaultCharset()).length - "-Dpadding=".length - 2
      val process = new ProcessBuilder(java, "-Dpadding=" + "x" * padding, s"-Didea.system.path=$system",
        "-cp", classes.toString, classOf[RuntimeProcessProbe].getName, ready.toString).inheritIO().start()
      try {
        val deadline = System.nanoTime() + 10.seconds.toNanos
        while (!Files.exists(ready) && process.isAlive && System.nanoTime() < deadline) Thread.sleep(10)
        Files.exists(ready) shouldBe true
        RuntimeProcesses.argumentsOf(process.info(), process.pid()).flatMap(RuntimeProcesses.pathsIn) shouldBe Some(Set(system))
      } finally { process.destroyForcibly(); process.waitFor() }
    }
  }
}
