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

  
  /**
   * Asserts that a directory contains exactly the expected files.
   * 
   * @param directory The directory to check
   * @param expectedFiles A sequence of expected filenames
   */
  def assertDirectoryContents(directory: File, expectedFiles: Seq[String]): Unit = {
    assertFileExists(directory)
    if (!directory.isDirectory) {
      fail(s"'$directory' is not a directory")
    }

    val expectedFilesSet = expectedFiles.toSet
    val actualFilesSet = directory.listFiles().map(_.getName).toSet

    val missingFiles = expectedFilesSet.diff(actualFilesSet)
    val unexpectedFiles = actualFilesSet.diff(expectedFilesSet)

    if (missingFiles.nonEmpty || unexpectedFiles.nonEmpty) {
      val missingMessage = if (missingFiles.nonEmpty) s"Missing files: ${missingFiles.mkString(", ")}" else ""
      val unexpectedMessage = if (unexpectedFiles.nonEmpty) s"Unexpected files: ${unexpectedFiles.mkString(", ")}" else ""
      val errorMessage = Seq(missingMessage, unexpectedMessage).filter(_.nonEmpty).mkString("\n")
      fail(s"Directory '$directory' contents don't match expected files:\n$errorMessage")
    }
  }
}
