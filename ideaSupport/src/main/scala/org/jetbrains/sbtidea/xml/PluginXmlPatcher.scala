package org.jetbrains.sbtidea.xml

import java.nio.file.{Files, Path}

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.{PluginLogger => log}
import org.jetbrains.sbtidea.Keys.pluginXmlOptions

class PluginXmlPatcher(input: Path, createCopy: Boolean = false) {

  def patch(options: pluginXmlOptions): Path = try {
    val content = new String(Files.readAllBytes(input))
    if (content.isEmpty) {
      log.error(s"Patching failed: $input exists but is empty")
      input
    } else {
      val result = transform(content, options)
      if (!createCopy)
        Files.write(input, result.getBytes)
      else
        Files.write(Files.createTempFile("", "plugin.xml"), result.getBytes)
    }
  } catch {
    case e: Exception =>
      log.error(s"Patching failed: $e")
      input
  }

  private def transform(input: String, options: pluginXmlOptions): String = {
    var content = input

    Option(options.version)
      .foreach( value => content = tag(content, "version", value))
    Option(options.pluginDescription)
      .foreach( value => content = tag(content, "description", value))
    Option(options.changeNotes)
      .foreach( value => content = tag(content, "change-notes", value))

    val ideaVersionTag = (Option(options.sinceBuild), Option(options.untilBuild)) match {
      case (Some(since), Some(until)) => s"""<idea-version since-build="$since" until-build="$until"/>"""
      case (None, Some(until))        => s"""<idea-version until-build="$until"/>"""
      case (Some(since), None)        => s"""<idea-version since-build="$since"/>"""
      case _ => ""
    }

    if (ideaVersionTag.nonEmpty)
      content = content.replaceAll("<idea-version.+/>", ideaVersionTag)

    content
  }

  private def tag(str: String, name: String, value: String): String =
    if (str.matches(s"(?s)^.*<$name>.+</$name>.*$$"))
    str.replaceAll(s"<$name>.+</$name>", s"<$name>$value</$name>")
  else {
    log.warn(s"$input doesn't have $name tag defined, not patching")
    str
  }

}
