package org.jetbrains.sbtidea.packaging

import org.scalatest.FeatureSpec

/**
  * dump the model from build with model_dumper_sbt.txt
  */
class LinearMappingsBuilderTest extends FeatureSpec with MappingsTestBase {

  private val revisionsToTest = Seq(
    "scio-idea-plugin",
    "sttp-bundle-IJE",
    "GH-106_scala-library-not-excluded",
    "d1950bef0ddfd50de365c45da2c0187e8e5e8cde",       // intellij-scala: TASTy: don't use "compile-internal" in build.sbt (workaround to fix project import)
    "d1950bef0ddfd50de365c45da2c0187e8e5e8cde-GH_106" // with a fix for #106 applied
  )


  feature("mappings equality on various builds") {
    revisionsToTest.foreach { rev =>
      scenario(s"revision: $rev") {
        testMappings(rev)
      }
    }
  }

  feature("same mappings and structure across SIP versions") {
    val pairs = Seq(
      "d1950bef0ddfd50de365c45da2c0187e8e5e8cde" -> "d1950bef0ddfd50de365c45da2c0187e8e5e8cde-GH_106")
    for ((a, b) <- pairs) {
      scenario(s"$a === $b") {
        val dataA = readTestData(a)
        val dataB = readTestData(b)

        val structureDiff = dataA.structure.toSet.diff(dataB.structure.toSet)
        val mappingsDiff = dataA.mappings.toSet.diff(dataB.mappings.toSet)
          .filterNot(_.from.toString.contains("/tmp")) // filter out temp files with random names

        structureDiff shouldBe empty
        mappingsDiff shouldBe empty
      }
    }
  }


}
