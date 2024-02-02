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
    // tested on Scala plugin version without qualified names grouping (less than 2024.1.4)
    RevisionTestDataDescription(
      "scioIdeaPlugin",
      RevisionReference(
        "https://github.com/spotify/scio-idea-plugin",
        "06ed4690",
        "Rename plugin according to verifier rules (#284)"
      )
    ),
    RevisionTestDataDescription(
      "sbtIdeaPlugin",
      RevisionReference(
        "https://github.com/JetBrains/sbt-idea-plugin",
        "e56f4bb6",
        "introduce usingFileSystem to avoid UnsupportedOperationException with default file system close method"
      )
    ),
    // note: zio-intellij was introduced because it contains module dependencies and the difference
    // between how it works with and without qualified names grouping is visible
    // (PackagedProjectNode.rootProjectName is not empty with qualified names grouping)
    RevisionTestDataDescription(
      "zio-intellij",
      RevisionReference(
        "https://github.com/zio/zio-intellij",
        "99aa4d54",
        "Initial support for IntelliJ 2024.1"
      )
    ),
    // tested on Scala plugin version with qualified names grouping (higher or equal 2024.1.4)
    RevisionTestDataDescription(
      "scioIdeaPlugin-qualified-names-grouping",
      RevisionReference(
        "https://github.com/spotify/scio-idea-plugin",
        "06ed4690",
        "Rename plugin according to verifier rules (#284)"
      )
    ),
    RevisionTestDataDescription(
      "zio-intellij-qualified-names-grouping",
      RevisionReference(
        "https://github.com/zio/zio-intellij",
        "99aa4d54",
        "Initial support for IntelliJ 2024.1"
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
