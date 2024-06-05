package org.jetbrains.sbtidea.download.plugin

import java.io.InputStream
import java.net.URL
import java.nio.file.{Files, Path}
import scala.xml.*
import scala.xml.Utility.escape

/**
 * Represents data located in `plugin.xml` file
 */
case class PluginDescriptor(id: String,
                            vendor: String,
                            name: String,
                            version: String,
                            sinceBuild: String,
                            untilBuild: String,
                            dependsOn: Seq[PluginDescriptor.Dependency] = Seq.empty) {

  private def sinceAttrVal: String =
    if (sinceBuild.nonEmpty) s"""since-build="$sinceBuild""""
    else ""

  private def untilAttrVal: String =
    if (untilBuild.nonEmpty) s"""until-build="$untilBuild""""
    else ""

  private def ideaVersion: String =
    if (sinceBuild.isEmpty && untilBuild.isEmpty) ""
    else s"""<idea-version $sinceAttrVal $untilAttrVal/>"""

  def toXMLStr: String = {
    s"""
       |<idea-plugin>
       |  <name>${escape(name)}</name>
       |  <vendor>${escape(vendor)}</vendor>
       |  <id>${escape(id)}</id>
       |  <version>$version</version>
       |  $ideaVersion
       |  ${dependsOn.map(dep => s"""<depends optional="${dep.optional}">${escape(dep.id)}</depends>""").mkString("\n")}
       |</idea-plugin>
       |""".stripMargin
  }

  /**
   * This method merges descriptors loaded from multiple files.
   *
   * Most of the plugins keep all information in single file `plugin.xml` (id, name, sinceBuild, etc...)<br>
   * However some plugins can keep this information in multiple files.
   * For example, "Code With Me" has different implementations for IDEA and Rider.
   * They keep common parts in `pluginBase.xml` (like id, name, vendor) and other parts (IDE-specific) in `plugin.xml` file.
   *
   * Alternative approach could be to truely resolve all "include" directives in `plugin.xml`, like: {{{
   *   <xi:include href="/META-INF/pluginBase.xml" xpointer="xpointer(/idea-plugin/)" />
   * }}}
   * However it would require more changes in existing sbt-idea-plugin code and it could be too much.
   */
  def merge(other: PluginDescriptor): PluginDescriptor = {
    def choose(accessor: PluginDescriptor => String): String = {
      val myValue = accessor(this)
      val otherValue = accessor(other)
      if (myValue.trim.nonEmpty) myValue else otherValue
    }
    PluginDescriptor(
      choose(_.id),
      choose(_.vendor),
      choose(_.name),
      choose(_.version),
      choose(_.sinceBuild),
      choose(_.untilBuild),
      dependsOn ++ other.dependsOn
    )
  }
}

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

  //noinspection ExistsEquals : scala 2.10
  def load(xml: Elem): PluginDescriptor = {
    val id      = (xml \\ "id").text
    val version = (xml \\ "version").text
    val name    = (xml \\ "name").text
    val vendor  = (xml \\ "vendor").text
    val since   = (xml \\ "idea-version").headOption.flatMap(tag => Option(tag.attributes("since-build")).map(_.text)).getOrElse("")
    val until   = (xml \\ "idea-version").headOption.flatMap(tag => Option(tag.attributes("until-build")).map(_.text)).getOrElse("")
    val dependencies = (xml \\ "depends").map { node =>
      val id        = node.text.replace(OPTIONAL_KEY, "")
      val optional  = node.text.contains(OPTIONAL_KEY) || node.attributes.asAttrMap.get(OPTIONAL_ATTR).exists(_ == "true")
      Dependency(id, optional)
    }
    val idOrName = if (id.isEmpty) name else id
    PluginDescriptor(idOrName, vendor, name, version, since, until, dependencies)
  }

//  private def requiredTag(xml: Elem, name: String): NodeSeq = {
//    val res = xml \\ name
//    if (res == null || res.isEmpty)
//      throw new IllegalArgumentException(s"Plugin descriptor xml doesn't have a required tag: $name")
//    res
//  }

  private def createNonValidatingParser: SAXParser = {
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
