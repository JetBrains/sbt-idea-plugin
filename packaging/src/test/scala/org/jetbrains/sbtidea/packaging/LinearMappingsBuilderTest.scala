package org.jetbrains.sbtidea.packaging

import org.scalatest.FeatureSpec

/**
  * dump the model from build with model_dumper_sbt.txt
  */
class LinearMappingsBuilderTest extends FeatureSpec with MappingsTestBase {

  private val revisionsToTest = Seq(
    "scio-idea-plugin",
    "d1950bef0ddfd50de365c45da2c0187e8e5e8cde" // intellij-scala: TASTy: don't use "compile-internal" in build.sbt (workaround to fix project import)
  )

  feature("mappings equality on various builds") {
      revisionsToTest.foreach { rev =>
        scenario(s"revision: $rev") {
          if (util.Properties.versionString.contains("2.10"))
            cancel("only for sbt 1.0+")
          else
            testMappings(rev)
        }
      }
  }

}
