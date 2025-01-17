package org.jetbrains.sbtidea.testUtils

import org.scalatest.Assertions.fail

import java.io.File
import java.nio.file.Files

object FileAssertions extends FileAssertions

trait FileAssertions {

  def assertFileExists(file: File): Unit = {
    if (!Files.exists(file.toPath)) {
      fail(s"File '$file' does not exist")
    }
  }

  def assertFileDoesNotExist(file: File): Unit = {
    if (Files.exists(file.toPath)) {
      fail(s"File '$file' exists, but it shouldn't exist")
    }
  }
}
