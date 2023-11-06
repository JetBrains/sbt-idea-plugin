package org.jetbrains.sbtidea.packaging

import org.scalatest.featurespec.AnyFeatureSpec

class LinearMappingsBuilderTest extends AnyFeatureSpec with MappingsTestBase {

  /**
   * NOTE: to update test data dump the model from build with `model_dumper_sbt.txt`<br>
   *
   * !!!ATTENTION:
   * Ensure that sbt version in the test repository equals to the sbt version in `build.sbt` in pluginCrossBuild / sbtVersion
   *
   * TODO: automate generation of test data?
   */
  private val testData: Seq[RevisionTestDataDescription] = Seq(
    RevisionTestDataDescription(
      "scioIdeaPlugin",
      RevisionReference(
        "https://github.com/spotify/scio-idea-plugin",
        "9654963",
        "Use patchPluginXml (#279)"
      )
    ),
    RevisionTestDataDescription(
      "sbtIdeaPlugin",
      RevisionReference(
        "https://github.com/JetBrains/sbt-idea-plugin",
        "b59ea8fa",
        "Merge pull request #123 from JetBrains/tobias/search-index"
      )
    )
  )


  Feature("mappings equality on various builds") {
    testData.foreach { rev =>
      val fileName = rev.testDataFileName
      Scenario(s"revision: $fileName") {
        testMappings(fileName)
      }
    }
  }
}
