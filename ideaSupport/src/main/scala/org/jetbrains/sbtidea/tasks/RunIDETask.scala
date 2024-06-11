package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.{productInfoExtraDataProvider, intellijBaseDirectory, intellijVMOptions}
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageArtifact
import org.jetbrains.sbtidea.runIdea.IdeaRunner
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.Keys.streams
import sbt.*

import scala.annotation.nowarn

object RunIDETask extends SbtIdeaInputTask[Unit] {
  @nowarn("msg=a pure expression does nothing in statement position")
  override def createTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    import complete.DefaultParsers.*
    packageArtifact.value // build the plugin before running
    PluginLogger.bind(new SbtPluginLogger(streams.value))
    val opts = spaceDelimited("[noDebug] [suspend] [blocking]").parsed
    val vmOptions = intellijVMOptions.value.copy(
      debug = !opts.contains("noDebug"),
      suspend = opts.contains("suspend")
    )
    val runner = new IdeaRunner(
      intellijBaseDirectory.value.toPath,
      productInfoExtraDataProvider.value,
      vmOptions,
      opts.contains("blocking")
    )
    runner.run()
  }
}
