package org.jetbrains.sbtidea.models

import org.jetbrains.sbtidea.models.XmlDecoder.createLoaderWithNonValidatingParser

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Path}
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, SAXParser, XML}

/**
 * A decoder that can decode an XML node into a model data class.
 * @tparam T type of the object to be decoded from XML
 */
trait XmlDecoder[T] extends Decoder[T, Elem] {
  override def decode(str: String): T =
    decode(createLoaderWithNonValidatingParser().loadString(str))

  override def decode(url: URL): T =
    decode(createLoaderWithNonValidatingParser().load(url))

  override def decode(path: Path): T =
    decode(createLoaderWithNonValidatingParser().load(Files.newInputStream(path)))

  override def decode(stream: InputStream): T =
    decode(createLoaderWithNonValidatingParser().load(stream))
}

object XmlDecoder {
  private def createLoaderWithNonValidatingParser(): XMLLoader[Elem] = XML.withSAXParser(createNonValidatingParser())

  private def createNonValidatingParser(): SAXParser = {
    val factory = javax.xml.parsers.SAXParserFactory.newInstance()
    factory.setValidating(false)
    factory.setFeature("http://xml.org/sax/features/validation", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.newSAXParser()
  }
}
