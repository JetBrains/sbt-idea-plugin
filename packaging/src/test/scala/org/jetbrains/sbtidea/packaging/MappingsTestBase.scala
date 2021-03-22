package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.ConsoleLogger
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackagedProjectNodeImpl
import org.scalatest.Matchers
import sbt._

import java.io.{File, ObjectInputStream}

trait MappingsTestBase extends Matchers with ConsoleLogger {

  case class Header(buildDir: File, userHome: File, outputDir: File)

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

  private def readStructure(revision: String): Seq[SbtPackagedProjectNodeImpl] =
    loadData(revision, "structure")

  private def readMappings(revision: String): Mappings =
    loadData(revision, "mappings")

  def testMappings(revision: String) {
    val header = loadHeader(revision)
    val structure = readStructure(revision)
    val mappings= readMappings(revision)
    val actualMappings = new LinearMappingsBuilder(header.outputDir, log).buildMappings(structure).map{ x=>
      x.copy(
        from = x.from.relativeTo(header.buildDir).orElse(x.from.relativeTo(header.userHome)).getOrElse(x.from),
        to   = x.to.relativeTo(header.buildDir).orElse(x.to.relativeTo(header.userHome)).getOrElse(x.to)
      )}
    mappings should contain theSameElementsAs actualMappings
  }
}
