package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageArtifact
import org.jetbrains.sbtidea.runIdea.CustomIntellijVMOptions.DebugInfo
import org.jetbrains.sbtidea.runIdea.IdeaRunner
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.*
import sbt.Keys.{name, streams}

import scala.annotation.nowarn

object RunIDETask extends SbtIdeaInputTask[Unit] {
  @nowarn("msg=a pure expression does nothing in statement position")
  override def createTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    import complete.DefaultParsers.*
    packageArtifact.value // build the plugin before running
    PluginLogger.bind(new SbtPluginLogger(streams.value))

    val opts = spaceDelimited("[noDebug] [suspend] [blocking]").parsed

    val customVmOptions = customIntellijVMOptions.value

    val debugAgentInfoPatched = if (opts.contains("noDebug"))
      None
    else
      Some(customVmOptions.debugInfo.getOrElse(DebugInfo.Default).copy(suspend = opts.contains("suspend")))

    val vmOptions = customVmOptions.copy(debugInfo = debugAgentInfoPatched)

    val runner = new IdeaRunner(
      intellijBaseDirectory = intellijBaseDirectory.value.toPath,
      productInfoExtraDataProvider = productInfoExtraDataProvider.value,
      vmOptions = vmOptions,
      vmOptionsBuilder = intellijVMOptionsBuilder.value,
      blocking = opts.contains("blocking"),
      discardOutput = false
    )
    runner.runWithRuntimeLogging("development", name.value, new SbtPluginLogger(streams.value))
  }
}
