package org.jetbrains.sbtidea.testUtils

import java.io.{File, FileInputStream, ObjectInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.asScalaBufferConverter

object IoUtils {
  def readLines(file: File): Iterable[String] =
    Files.readAllLines(file.toPath, StandardCharsets.UTF_8).asScala

  def writeStringToFile(file: File, content: String): Path = {
    Files.write(file.toPath, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
  }

  def readBinaryData[T](file: File): T = {
    val stream = new FileInputStream(file)
    val reader = new ObjectInputStream(stream)
    val data = reader.readObject().asInstanceOf[T]
    reader.close()
    data
  }
}
