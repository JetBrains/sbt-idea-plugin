package org.jetbrains.sbtidea.xml

class pluginXmlOptions {
  var version: String = _
  var sinceBuild: String = _
  var untilBuild: String = _
  var pluginDescription: String = _
  var changeNotes: String = _

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
