package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackagedProjectNodeImpl
import org.scalatest.matchers.should.Matchers
import sbt.*

import java.io.{File, ObjectInputStream}

trait MappingsTestBase extends Matchers {

  case class Header(buildDir: File, userHome: File, outputDir: File)

  case class TestData(header: Header, structure: Seq[SbtPackagedProjectNodeImpl], mappings: Mappings)

  private def loadHeader(revision: String): Header = {
    val stream = getClass.getResourceAsStream(s"$revision.dat")
    val reader = new ObjectInputStream(stream)
    val result = Header(
      new File(reader.readUTF()),
      new File(reader.readUTF()),
      new File(reader.readUTF())
    )
    reader.close()
    result
  }

  private def loadData[T](revision: String, suffix: String): T = {
    val stream = getClass.getResourceAsStream(s"$revision-$suffix.dat")
    val reader = new ObjectInputStream(stream)
    val data = reader.readObject().asInstanceOf[T]
    reader.close()
    data
  }

  protected def readStructure(revision: String): Seq[SbtPackagedProjectNodeImpl] =
    loadData(revision, "structure")

  protected def readMappings(revision: String): Mappings =
    loadData(revision, "mappings")

  protected def readTestData(revision: String): TestData = {
    val header = loadHeader(revision)
    val structure = readStructure(revision)
    val mappings = readMappings(revision)
    TestData(header, structure, mappings)
  }

  def testMappings(testDataFileName: String): Unit = {
    val header = loadHeader(testDataFileName)
    val mappings = readMappings(testDataFileName)
    val structure = readStructure(testDataFileName)

    val mappingsBuilder = new LinearMappingsBuilder(header.outputDir, PluginLogger)
    val actualMappings = mappingsBuilder.buildMappings(structure).map { m =>
      val fromNew = m.from.relativeTo(header.buildDir).orElse(m.from.relativeTo(header.userHome)).getOrElse(m.from)
      val toNew = m.to.relativeTo(header.buildDir).orElse(m.to.relativeTo(header.userHome)).getOrElse(m.to)
      m.copy(from = fromNew, to = toNew)
    }
    mappings should contain theSameElementsAs actualMappings
  }
}
