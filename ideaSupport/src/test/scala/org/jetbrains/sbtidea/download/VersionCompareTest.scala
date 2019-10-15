package org.jetbrains.sbtidea.download

import org.scalatest.{FunSuite, Matchers}

class VersionCompareTest extends FunSuite with Matchers {

  test("versions greater") {
    val validCombos = Seq(
      "1" -> "0",
      "1.0" -> "1",
      "1.1" -> "0",
      "1.1" -> "1.0",
      "1.2.3" -> "1.1.9999"
    )

    for ((a, b) <- validCombos)
      withClue(s"$a > $b: ") {
        IdeaPluginInstaller.compareVersions(a, b) shouldBe > (0)
      }
  }

  test("versions equal") {
    val validCombos = Seq(
      "0" -> "0",
      "1.0" -> "1.0",
      "1.1" -> "1.1",
      "1.1.1" -> "1.1.1"
    )

    for ((a, b) <- validCombos)
      withClue(s"$a == $b: ") {
        IdeaPluginInstaller.compareVersions(a, b) shouldBe 0
      }
  }

  test("version wildcards greater") {
    val validCombos = Seq(
      "*" -> "0",
      "*" -> "999",
      "1.*" -> "1.999",
      "999.*" -> "1.1",
      "999.1" -> "1.*",
      "1.1.*" -> "1.1.999"
    )

    for ((a, b) <- validCombos)
      withClue(s"$a > $b: ") {
        IdeaPluginInstaller.compareVersions(a, b) shouldBe > (0)
      }
  }

}
