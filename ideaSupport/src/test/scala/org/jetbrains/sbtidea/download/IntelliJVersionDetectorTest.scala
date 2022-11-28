package org.jetbrains.sbtidea.download

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.io.File

class IntelliJVersionDetectorTest extends AnyFunSuite {

  import IntelliJVersionDetector.*

  test("readVersionFromBuildTxt") {
    readVersionFromBuildTxt("IC-222.3739.54") shouldBe Some(Version("222.3739.54"))
    readVersionFromBuildTxt("IU-222.3739.54") shouldBe Some(Version("222.3739.54"))
    readVersionFromBuildTxt("   IU-222.3739.54   ") shouldBe Some(Version("222.3739.54"))

    readVersionFromBuildTxt("222.3739.54") shouldBe Some(Version("222.3739.54"))
    readVersionFromBuildTxt("222.3739") shouldBe Some(Version("222.3739"))
    readVersionFromBuildTxt("222") shouldBe None
  }

  test("readVersionFromIntellijDirectory") {
    readVersionFromIntellijDirectory(new File("/my/path/222.3739.54")) shouldBe Some(Version("222.3739.54"))
    readVersionFromIntellijDirectory(new File("/my/path/222.3739")) shouldBe Some(Version("222.3739"))
    readVersionFromIntellijDirectory(new File("/my/path/222")) shouldBe None
  }
}
