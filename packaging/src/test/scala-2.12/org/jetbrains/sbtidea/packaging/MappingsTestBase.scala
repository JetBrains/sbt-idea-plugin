package org.jetbrains.sbtidea.packaging

import io.circe.Decoder
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.MappingsTestBase.{Header, TestData}
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackagedProjectNodeImpl
import org.scalatest.AppendedClues.convertToClueful
import org.scalatest.matchers.should.Matchers
import sbt.fileToRichFile

import java.io.{File, FileInputStream, ObjectInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

trait MappingsTestBase extends Matchers {

  private val testDataDir: File = new File("./packaging/testData").getCanonicalFile

  private def loadHeader(revision: String): Header = {
    val file = new File(testDataDir, s"$revision.dat")
    val stream = new FileInputStream(file)
    val reader = new ObjectInputStream(stream)
    val result = Header(
      new File(reader.readUTF()),
      new File(reader.readUTF()),
      new File(reader.readUTF())
    )
    reader.close()
    result
  }

  private def loadTestData[T](revision: String, suffix: String): T = {
    val file = new File(testDataDir, s"$revision-$suffix.dat")
    val stream = new FileInputStream(file)
    val reader = new ObjectInputStream(stream)
    val data = reader.readObject().asInstanceOf[T]
    reader.close()
    data
  }

  private def writeToTestData(text: String, relativeFilePath: String): Unit = {
    val path = Paths.get(testDataDir.toPath.toString, relativeFilePath)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
  }

  protected def readStructure(revision: String): Seq[SbtPackagedProjectNodeImpl] =
    loadTestData(revision, "structure")

  protected def readMappings(revision: String): Mappings =
    loadTestData(revision, "mappings")

  protected def readTestData(revision: String): TestData = {
    val header = loadHeader(revision)
    val structure = readStructure(revision)
    val mappings = readMappings(revision)
    TestData(header, structure, mappings)
  }

  /**
   * @param testDataBaseFileName example: "sbtIdeaPlugin" or "scioIdeaPlugin" or "zio-intellij"
   */
  def testMappings(testDataBaseFileName: String): Unit = {
    val header = loadHeader(testDataBaseFileName)
    val mappings = readMappings(testDataBaseFileName)
    val structure = readStructure(testDataBaseFileName)

    import io.circe.syntax.*
    import CirceEncodersDecoders.*
    
    // Decode the JSON strings back into their respective objects without extra checks
    val headerJson = header.asJson.noSpaces
    val mappingsJson = mappings.asJson.noSpaces
    val structureJson = structure.asJson.noSpaces

    val decodedHeader = decodeJson[Header](headerJson, "Header")
    val decodedMappings = decodeJson[Mappings](mappingsJson, "Mappings")
    val decodedStructure = decodeJson[Seq[SbtPackagedProjectNodeImpl]](structureJson, "Structure")
    
    // Assert that in-memory objects are equivalent to their original counterparts
    // This ensures the serialization and deserialization produce consistent results for the Header, Mappings, and Structure
    decodedHeader shouldEqual header withClue "Deserialized Header does not match the original"
    decodedMappings shouldEqual mappings withClue "Deserialized Mappings do not match the original"
    decodedStructure shouldEqual structure withClue "Deserialized Structure does not match the original"

    writeToTestData(headerJson, s"$testDataBaseFileName-header.json")
    writeToTestData(mappingsJson, s"$testDataBaseFileName-mappings.json")
    writeToTestData(structureJson, s"$testDataBaseFileName-structure.json")

    val mappingsBuilder = new LinearMappingsBuilder(header.outputDir, PluginLogger)
    val actualMappings = mappingsBuilder.buildMappings(structure).map { m =>
      val fromNew = m.from.relativeTo(header.buildDir).orElse(m.from.relativeTo(header.userHome)).getOrElse(m.from)
      val toNew = m.to.relativeTo(header.buildDir).orElse(m.to.relativeTo(header.userHome)).getOrElse(m.to)
      m.copy(from = fromNew, to = toNew)
    }
    mappings should contain theSameElementsAs actualMappings
  }

  private def decodeJson[T : Decoder](json: String, typeName: String): T =
    io.circe.parser.decode[T](json) match {
      case Right(value) => value
      case Left(error) =>
        throw new RuntimeException(s"Failed to decode $typeName: ${error.getMessage}", error)
    }
}

object MappingsTestBase {
  case class Header(buildDir: File, userHome: File, outputDir: File)
  case class TestData(header: Header, structure: Seq[SbtPackagedProjectNodeImpl], mappings: Mappings)
}
