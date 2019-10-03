package org.jetbrains.sbtidea.xml

import java.io.File

class pluginXmlOptions {
  var version: Option[String] = None
  var sinceBuild: Option[String] = None
  var untilBuild: Option[String] = None
  var pluginDescription: Option[String] = None
  var changeNotes: Option[String] = None
  var pluginXmlFiles: Seq[File] = _
  var destinationDir: File = _

  def apply(func: pluginXmlOptions => Unit): pluginXmlOptions = { func(this); this }
}

object pluginXmlOptions {
  val DISABLED = new pluginXmlOptions()

  def apply(init: pluginXmlOptions => Unit): pluginXmlOptions = {
    val xml = new pluginXmlOptions()
    init(xml)
    xml
  }

}
