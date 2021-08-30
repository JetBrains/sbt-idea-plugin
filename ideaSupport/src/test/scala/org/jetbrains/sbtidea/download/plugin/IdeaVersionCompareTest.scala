package org.jetbrains.sbtidea.download.plugin

import org.scalatest.{FunSuite, Matchers}
import RepoPluginInstaller.compareIdeaVersions
import org.jetbrains.sbtidea.download.Version

class IdeaVersionCompareTest extends FunSuite with Matchers {

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
        compareIdeaVersions(a, b) shouldBe > (0)
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
        compareIdeaVersions(a, b) shouldBe 0
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
        compareIdeaVersions(a, b) shouldBe > (0)
      }
  }

  test("version case class") {
    import scala.math.Ordering.Implicits.infixOrderingOps
    assert(Version("203.5251") < Version("213.2732"))
    assert(Version("213.2732") > Version("203.5251"))
    assert(Version("213.2732") == Version("213.2732"))
  }
}
