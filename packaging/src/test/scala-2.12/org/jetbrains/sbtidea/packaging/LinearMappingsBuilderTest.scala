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
  private val withoutQualifiedNamesGrouping: Seq[RevisionTestDataDescription] = Seq(
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
    )
  )
  private val withQualifiedNamesGrouping: Seq[RevisionTestDataDescription] = Seq(
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
  private val withSeparateProdTestSources: Seq[RevisionTestDataDescription] = Seq(
    RevisionTestDataDescription(
      "zio-inteliij-prod-test-sources",
      RevisionReference(
        "https://github.com/zio/zio-intellij",
        "ee40b293",
        "Test fixtures upgrade"
      )
    )
  )

  runFeatureTest("mappings equality on builds without qualified names grouping", withoutQualifiedNamesGrouping)
  runFeatureTest("mappings equality on builds with qualified names grouping", withQualifiedNamesGrouping)
  runFeatureTest("mappings equality on builds with prod/test sources separated", withSeparateProdTestSources, sepProdTest = true)

  private def runFeatureTest(featureDesc: String, revisionTestData: Seq[RevisionTestDataDescription], sepProdTest: Boolean = false): Unit =
    Feature(featureDesc) {
      revisionTestData.foreach { rev =>
        val fileName = rev.testDataFileName
        Scenario(s"revision: $fileName") {
          runTestMappings(fileName, sepProdTest)
        }
      }
    }

  private def runTestMappings(fileName: String, sepProdTest: Boolean = false): Unit =
    if (sepProdTest) {
      try {
        System.setProperty(ProdTestSourcesKey, "true")
        testMappings(fileName)
      } finally {
        System.clearProperty(ProdTestSourcesKey)
      }
    } else {
      testMappings(fileName)
    }
}
