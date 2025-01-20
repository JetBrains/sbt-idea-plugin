package org.jetbrains.sbtidea.packaging

import io.circe.Decoder
import io.circe.syntax.*
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.MappingsTestBase.TestData
import org.jetbrains.sbtidea.packaging.RegenerateProjectsStructureTestData.PathMacroKeys
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackagedProjectNodeImpl
import org.jetbrains.sbtidea.packaging.testUtils.CirceEncodersDecoders.*
import org.jetbrains.sbtidea.packaging.testUtils.{JsonUtils, TestDataDir}
import org.scalatest.AppendedClues.convertToClueful
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * @note to regenerate test data to the most up-to-date model please use [[RegenerateProjectsStructureTestData]]
 * @note for end-to-end integration tests see SbtIdeaPluginIntegrationTest
 */
trait MappingsTestBase extends Matchers {

  private def loadTestData[T : Decoder](revision: String, suffix: String): T = {
    val file = new File(TestDataDir, s"$revision-$suffix.json")
    val jsonString = new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)
    JsonUtils.decodeJson[T](jsonString, "Header")
  }

  protected def readStructure(revision: String): Seq[SbtPackagedProjectNodeImpl] =
    loadTestData[Seq[SbtPackagedProjectNodeImpl]](revision, "structure")

  protected def readMappings(revision: String): Mappings =
    loadTestData[Seq[Mapping]](revision, "mappings")

  protected def readTestData(revision: String): TestData = {
    val structure = readStructure(revision)
    val mappings = readMappings(revision)
    TestData(structure, mappings)
  }

  /**
   * @param testDataBaseFileName example: "sbtIdeaPlugin" or "scioIdeaPlugin" or "zio-intellij"
   */
  def testMappings(testDataBaseFileName: String): Unit = {
    val mappings = readMappings(testDataBaseFileName)
    val structure = readStructure(testDataBaseFileName)

    // Assert that in-memory objects are equivalent to their original counterparts
    // This ensures the serialization and deserialization produce consistent results for the Header, Mappings, and Structure
    {
      // Decode the JSON strings back into their respective objects
      val mappingsJson = mappings.asJson.noSpaces
      val structureJson = structure.asJson.noSpaces

      val decodedMappings = JsonUtils.decodeJson[Mappings](mappingsJson, "Mappings")
      val decodedStructure = JsonUtils.decodeJson[Seq[SbtPackagedProjectNodeImpl]](structureJson, "Structure")

      decodedMappings shouldEqual mappings withClue "Deserialized Mappings do not match the original"
      decodedStructure shouldEqual structure withClue "Deserialized Structure does not match the original"
    }

    val mappingsBuilder = new LinearMappingsBuilder(new File(PathMacroKeys.PluginOutput), PluginLogger)
    val actualMappings = mappingsBuilder.buildMappings(structure)
    actualMappings shouldEqual mappings
  }
}

object MappingsTestBase {

  case class TestData(structure: Seq[SbtPackagedProjectNodeImpl], mappings: Mappings)
}
