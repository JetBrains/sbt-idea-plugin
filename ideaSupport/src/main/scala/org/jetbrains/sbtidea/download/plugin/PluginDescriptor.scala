package org.jetbrains.sbtidea.download.plugin

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Path}

import scala.xml._

case class PluginDescriptor(id: String,
                            name: String,
                            version: String,
                            sinceBuild: String,
                            untilBuild: String,
                            dependsOn: Seq[PluginDescriptor.Dependency] = Seq.empty)

object PluginDescriptor {

  private val OPTIONAL_KEY  = "(optional) "
  private val OPTIONAL_ATTR = "optional"

  final case class Dependency(id: String, optional: Boolean)

  def load(str: String): PluginDescriptor =
    load(XML.withSAXParser(createNonValidatingParser).loadString(str))

  def load(url: URL): PluginDescriptor =
    load(XML.withSAXParser(createNonValidatingParser).load(url))

  def load(path: Path): PluginDescriptor =
    load(XML.withSAXParser(createNonValidatingParser).load(Files.newInputStream(path)))

  def load(stream: InputStream): PluginDescriptor =
    load(XML.withSAXParser(createNonValidatingParser).load(stream))

  def load(xml: Elem): PluginDescriptor = {
    val id      = (xml \\ "id").text
    val version = (xml \\ "version").text
    val name    = (xml \\ "name").text
    val since   = (xml \\ "idea-version").headOption.map(_.attributes("since-build").text).getOrElse("")
    val until   = (xml \\ "idea-version").headOption.map(_.attributes("until-build").text).getOrElse("")
    val dependencies = (xml \\ "depends").map { node =>
      val id        = node.text.replace(OPTIONAL_KEY, "")
      val optional  = node.text.contains(OPTIONAL_KEY) || node.attributes(OPTIONAL_ATTR) != null
      Dependency(id, optional)
    }
    val idOrName = if (id.isEmpty) name else id
    PluginDescriptor(idOrName, name, version, since, until, dependencies)
  }

  private def createNonValidatingParser = {
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
