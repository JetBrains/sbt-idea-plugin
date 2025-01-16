package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.LinearMappingsBuilderTest.runWithSeparateProdTestSourcesEnabled
import org.jetbrains.sbtidea.packaging.testUtils.RevisionReference
import org.scalatest.featurespec.AnyFeatureSpec

class LinearMappingsBuilderTest extends AnyFeatureSpec with MappingsTestBase {

  Feature("mappings equality on builds without qualified names grouping") {
    LinearMappingsBuilderTest.TestDataDescriptions.foreach { td =>
      val fileName = td.testDataFileName
      val separateProductionTestSources = td.useSeparateProductionTestSources
      Scenario(s"revision: $fileName") {
        runTestMappings(fileName, sepProdTest = separateProductionTestSources)
      }
    }
  }

  //TODO: we probably should set some property for qualified names grouping before adding this test data
  //val withQualifiedNamesGrouping: Seq[TestDataDescription] = Seq(
  //  TestDataDescription("scioIdeaPlugin-qualified-names-grouping", scio),
  //  TestDataDescription("zio-intellij-qualified-names-grouping", zioIntellij)
  //)
  //runFeatureTest("mappings equality on builds with qualified names grouping", withQualifiedNamesGrouping)

  private def runTestMappings(fileName: String, sepProdTest: Boolean = false): Unit =
    if (sepProdTest) {
      runWithSeparateProdTestSourcesEnabled {
        testMappings(fileName)
      }
    } else {
      testMappings(fileName)
    }
}

object LinearMappingsBuilderTest {

  case class TestDataDescription(
    testDataFileName: String,
    revisionRef: RevisionReference,
    useSeparateProductionTestSources: Boolean = false
  )

  def runWithSeparateProdTestSourcesEnabled[T](body: => T): Unit = {
    try {
      System.setProperty(ProdTestSourcesKey, "true")
      body
    } finally {
      System.clearProperty(ProdTestSourcesKey)
    }
  }

  import org.jetbrains.sbtidea.packaging.testUtils.ReposRevisions.*

  val TestDataDescriptions: Seq[TestDataDescription] = Seq(
    TestDataDescription("scioIdeaPlugin", scio),
    TestDataDescription("sbtIdeaPlugin", sbtIdePlugin),
    // note: zio-intellij was introduced because it contains module dependencies and the difference
    // between how it works with and without qualified names grouping is visible
    // (PackagedProjectNode.rootProjectName is not empty with qualified names grouping)
    TestDataDescription("zio-intellij", zioIntellij),
    TestDataDescription("zio-intellij-prod-test-sources", zioIntellij, useSeparateProductionTestSources = true)
  )
}