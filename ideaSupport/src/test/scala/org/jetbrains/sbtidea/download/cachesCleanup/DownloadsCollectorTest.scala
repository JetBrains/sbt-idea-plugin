package org.jetbrains.sbtidea.download.cachesCleanup

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files
import java.time.LocalDate

class DownloadsCollectorTest extends AnyFunSuite {

  test("collectDownloads should return empty report for non-existent directory") {
    val nonExistentDir = Files.createTempDirectory("test").resolve("non-existent")
    val report = DownloadsCollector.collectDownloads(nonExistentDir)
    
    assert(report.fileInfos.isEmpty)
    assert(report.baseDirectory == nonExistentDir)
  }

  test("collectDownloads should return empty report for empty directory") {
    val tempDir = Files.createTempDirectory("test-downloads")
    try {
      val report = DownloadsCollector.collectDownloads(tempDir)
      
      assert(report.fileInfos.isEmpty)
      assert(report.baseDirectory == tempDir)
    } finally {
      Files.deleteIfExists(tempDir)
    }
  }

  test("collectDownloads should collect files from directory") {
    val tempDir = Files.createTempDirectory("test-downloads")
    try {
      // Create test files
      val file1 = Files.createFile(tempDir.resolve("file1.zip"))
      val file2 = Files.createFile(tempDir.resolve("file2.jar"))
      
      val report = DownloadsCollector.collectDownloads(tempDir)
      
      assert(report.fileInfos.length == 2)
      assert(report.baseDirectory == tempDir)
      
      val filePaths = report.fileInfos.map(_.path.getFileName.toString).toSet
      assert(filePaths.contains("file1.zip"))
      assert(filePaths.contains("file2.jar"))
      
      // Check that creation dates are reasonable (within last day)
      val yesterday = LocalDate.now().minusDays(1)
      val tomorrow = LocalDate.now().plusDays(1)
      report.fileInfos.foreach { fileInfo =>
        assert(fileInfo.metaData.creationDate.isAfter(yesterday))
        assert(fileInfo.metaData.creationDate.isBefore(tomorrow))
      }
    } finally {
      // Clean up
      Files.walk(tempDir).sorted((a, b) => b.compareTo(a)).forEach(Files.deleteIfExists(_))
    }
  }
}