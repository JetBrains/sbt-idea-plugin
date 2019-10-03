package org.jetbrains.sbtidea.xml

import java.nio.file.{Files, Path}

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.BuildInfo

class PluginXmlPatcher(input: Path, buildInfo: BuildInfo, createCopy: Boolean = false)(log: PluginLogger) {

  def patch(options: pluginXmlOptions): Path = try {
    val content = new String(Files.readAllBytes(input))
    if (content.isEmpty) {
      log.error(s"Patching failed: $input exists but is empty")
      input
    } else {
      val result = transform(content, options)
      if (!createCopy)
        Files.writeString(input, result)
      else
        Files.writeString(Files.createTempFile("", "plugin.xml"), content)
    }
  } catch {
    case e: Exception =>
      log.error(s"Patching failed: $e")
      input
  }

  private def transform(input: String, options: pluginXmlOptions): String = {
    var content = input

    options.version
      .foreach( value => content = tag(content, "version", value))
    options.pluginDescription
      .foreach( value => content = tag(content, "description", value))
    options.changeNotes
      .foreach( value => content = tag(content, "change-notes", value))

    val ideaVersionTag = (options.sinceBuild, options.untilBuild) match {
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
    str.replaceAll(s"<$name>.+</$name>", s"<$name>$value")

}
