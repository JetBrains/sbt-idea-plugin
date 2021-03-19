package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.{intellijMainJars, intellijVMOptions}
import org.jetbrains.sbtidea.{PluginLogger, SbtPluginLogger}
import org.jetbrains.sbtidea.packaging.PackagingKeys.packageArtifact
import org.jetbrains.sbtidea.runIdea.IdeaRunner
import sbt.{Def, _}
import sbt.Keys.streams

object RunIDETask extends SbtIdeaInputTask[Unit] {
  override def createTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    import complete.DefaultParsers._
    packageArtifact.value // build the plugin before running
    PluginLogger.bind(new SbtPluginLogger(streams.value))
    val opts = spaceDelimited("[noPCE] [noDebug] [suspend] [blocking]").parsed
    val vmOptions = intellijVMOptions.value.copy(
      noPCE = opts.contains("noPCE"),
      debug = !opts.contains("noDebug"),
      suspend = opts.contains("suspend")
    )
    val ideaCP = intellijMainJars.value.map(_.data.toPath)
    val runner = new IdeaRunner(ideaCP, vmOptions, opts.contains("blocking"))
    runner.run()
  }

}
