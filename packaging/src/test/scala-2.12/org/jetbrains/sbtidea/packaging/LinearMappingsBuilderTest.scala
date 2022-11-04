package org.jetbrains.sbtidea.packaging

import org.scalatest.featurespec.AnyFeatureSpec

/**
  * dump the model from build with model_dumper_sbt.txt
  */
class LinearMappingsBuilderTest extends AnyFeatureSpec with MappingsTestBase {

  private val revisionsToTest = Seq(
    "scio-idea-plugin",
    "sttp-bundle-IJE",
    "GH-106_scala-library-not-excluded"
  )


  Feature("mappings equality on various builds") {
    revisionsToTest.foreach { rev =>
      Scenario(s"revision: $rev") {
        testMappings(rev)
      }
    }
  }

  Feature("same mappings and structure across SIP versions") {
    val pairs = Seq(
      "d1950bef0ddfd50de365c45da2c0187e8e5e8cde" -> "d1950bef0ddfd50de365c45da2c0187e8e5e8cde-GH_106")
    for ((a, b) <- pairs) {
      Scenario(s"$a === $b") {
        val dataA = readTestData(a)
        val dataB = readTestData(b)

        val structureDiff = dataA.structure.toSet.diff(dataB.structure.toSet)
        val mappingsDiff = dataA.mappings.toSet.diff(dataB.mappings.toSet)
          .filterNot(_.from.toString.replace("\\", "/").contains("/tmp")) // filter out temp files with random names

        structureDiff shouldBe empty
        mappingsDiff shouldBe empty
      }
    }
  }


}
