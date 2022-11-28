package org.jetbrains.sbtidea.xml

import sbt.*

import java.nio.file.Path

object PluginXmlDetector {
  def getPluginXml(targetDir: Path): Option[Path] = {
    val path = targetDir / "META-INF" / "plugin.xml"
    if (path.toFile.exists())
      Some(path)
    else None
  }
}
