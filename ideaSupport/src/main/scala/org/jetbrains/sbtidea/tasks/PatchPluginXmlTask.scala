package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.xml.{PluginXmlDetector, PluginXmlPatcher}
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.Keys.{productDirectories, streams}
import sbt.{Def, *}

object PatchPluginXmlTask extends SbtIdeaTask[Unit] {
  override def createTask: Def.Initialize[Task[Unit]] = Def.task {
    PluginLogger.bind(new SbtPluginLogger(streams.value))
    val options = patchPluginXml.value
    val productDirs = productDirectories.in(Compile).value
    if (options != pluginXmlOptions.DISABLED) {
      val detectedXmls = productDirs.flatMap(f => PluginXmlDetector.getPluginXml(f.toPath))
      PluginLogger.info(s"Detected plugin xmls, patching: ${detectedXmls.mkString("\n")}")
      detectedXmls.foreach { xml => new PluginXmlPatcher(xml).patch(options) }
    }
  }
}
