package org.jetbrains.sbtidea.download

import java.nio.file.Path

import org.jetbrains.sbtidea.download.api._
import org.jetbrains.sbtidea.download.idea.{IdeaDependency, IdeaDist}
import org.jetbrains.sbtidea.download.plugin.{LocalPluginRegistry, PluginDependency, PluginRepoUtils}
import org.jetbrains.sbtidea.Keys.IntellijPlugin
import org.jetbrains.sbtidea.{PluginLogger => log}
import org.jetbrains.sbtidea.download.jbr.JbrDependency

class CommunityUpdater(baseDirectory: Path, ideaBuildInfo: BuildInfo, plugins: Seq[IntellijPlugin], withSources: Boolean = true) {

  implicit private val context: InstallContext =
    InstallContext(baseDirectory = baseDirectory, downloadDirectory = baseDirectory.getParent)

  implicit private val remoteRepoApi: PluginRepoUtils =
    new PluginRepoUtils

  implicit private val localRegistry: LocalPluginRegistry =
    new LocalPluginRegistry(baseDirectory)

  protected val ideaDependency: IdeaDependency = IdeaDependency(ideaBuildInfo)

  protected def dependencies: Seq[UnresolvedArtifact] =
    ideaDependency                                                      +:
      JbrDependency(baseDirectory, ideaBuildInfo, Seq(ideaDependency))  +:
      plugins.map(pl => PluginDependency(pl, ideaBuildInfo, Seq(ideaDependency)))

  def update(): Unit = topoSort(dependencies).foreach(update)

  def update(dependency: UnresolvedArtifact): Unit = {
    val resolved = dependency.resolve
    val (installed, nonInstalled) = resolved.partition(_.isInstalled)
    val numMissing = nonInstalled.size
    val numInstalled = installed.size
    val numTotal = installed.size + nonInstalled.size

    if (resolved.isEmpty)
      log.warn(s"- Nothing resolved for $dependency")
    else if (nonInstalled.nonEmpty)
      log.info(s"~ Resolving $dependency -> $numMissing/$numTotal new artifacts")
    else
      log.info(s"+ $dependency is up to date: $numInstalled/$numTotal")

    nonInstalled.foreach(_.install)
  }

  private[download] def topoSort(deps: Seq[UnresolvedArtifact]): Seq[UnresolvedArtifact] = {
    val indexed = topoSortImpl(deps)
    indexed
      .sortBy(- _._2)
      .map(_._1)
      .distinct
  }

  private def topoSortImpl(deps: Seq[UnresolvedArtifact], level: Int = 0): Seq[(UnresolvedArtifact, Int)] = {
    deps.map(_ -> level) ++ deps.flatMap(dep => topoSortImpl(dep.dependsOn, level+1))
  }
}
