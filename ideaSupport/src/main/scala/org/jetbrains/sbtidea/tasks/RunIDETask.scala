package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.*
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageArtifact
import org.jetbrains.sbtidea.runIdea.CustomIntellijVMOptions.DebugInfo
import org.jetbrains.sbtidea.runIdea.IdeaRunner
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.VmOptions
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import sbt.*
import sbt.Keys.streams

import scala.annotation.nowarn

object RunIDETask extends SbtIdeaInputTask[Unit] {
  @nowarn("msg=a pure expression does nothing in statement position")
  override def createTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    import complete.DefaultParsers.*
    packageArtifact.value // build the plugin before running
    PluginLogger.bind(new SbtPluginLogger(streams.value))

    val opts = spaceDelimited("[noDebug] [suspend] [blocking]").parsed

    val customVmOptions = customIntellijVMOptions.value
    val legacyVmOptions = intellijVMOptions.value: @nowarn("cat=deprecation")

    val debugAgentInfoPatched = if (opts.contains("noDebug"))
      None
    else
      Some(customVmOptions.debugInfo.getOrElse(DebugInfo.Default).copy(suspend = opts.contains("suspend")))


    val vmOptions: VmOptions = if (useNewVmOptions.value)
      VmOptions.New(customIntellijVMOptions.value.copy(debugInfo = debugAgentInfoPatched))
    else
      VmOptions.Old(intellijVMOptions.value.copy(
        debug = !opts.contains("noDebug"),
        suspend = opts.contains("suspend")
      )): @nowarn("cat=deprecation")

    val runner = new IdeaRunner(
      intellijBaseDirectory = intellijBaseDirectory.value.toPath,
      productInfoExtraDataProvider = productInfoExtraDataProvider.value,
      vmOptions = vmOptions,
      vmOptionsBuilder = intellijVMOptionsBuilder.value,
      blocking = opts.contains("blocking"),
      discardOutput = false
    )
    runner.run()
  }
}
