package org.jetbrains.sbtidea.download.cachesCleanup

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Paths
import java.time.LocalDate

class OldDownloadsDetectorTest extends AnyFunSuite with WithMockedTime {

  test("detectOldDownloads should identify files older than 1 month") {
    val oldDate = mockedNow().minusDays(40)
    val recentDate = mockedNow().minusDays(15)
    
    val oldFile = FileMetaInfo(
      Paths.get("/tmp/old-file.zip"),
      DirectoryMetaData(oldDate)
    )
    
    val recentFile = FileMetaInfo(
      Paths.get("/tmp/recent-file.zip"),
      DirectoryMetaData(recentDate)
    )
    
    val report = DownloadsReport(
      Seq(oldFile, recentFile),
      Paths.get("/tmp")
    )
    
    val oldDownloads = OldDownloadsDetector.detectOldDownloads(report)
    
    assert(oldDownloads.length == 1)
    assert(oldDownloads.head == oldFile)
  }

  test("detectOldDownloads should return empty list when no files are old") {
    val recentDate = mockedNow().minusDays(15)
    
    val recentFile = FileMetaInfo(
      Paths.get("/tmp/recent-file.zip"),
      DirectoryMetaData(recentDate)
    )
    
    val report = DownloadsReport(
      Seq(recentFile),
      Paths.get("/tmp")
    )
    
    val oldDownloads = OldDownloadsDetector.detectOldDownloads(report)
    
    assert(oldDownloads.isEmpty)
  }
}