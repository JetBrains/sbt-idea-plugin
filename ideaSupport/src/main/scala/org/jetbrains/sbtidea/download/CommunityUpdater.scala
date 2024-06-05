package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.download.idea.IdeaDependency
import org.jetbrains.sbtidea.download.jbr.JbrDependency
import org.jetbrains.sbtidea.download.plugin.{LocalPluginRegistry, PluginDependency, PluginRepoUtils}
import org.jetbrains.sbtidea.{IntellijPlugin, JbrInfo, PluginLogger as log}

import java.nio.file.Path

class CommunityUpdater(
  baseDirectory: Path,
  ideaBuildInfo: BuildInfo,
  jbrInfo: JbrInfo,
  plugins: Seq[IntellijPlugin],
  //noinspection ScalaUnusedSymbol (can be used by sbt plugin users)
  withSources: Boolean = true
) {

  implicit protected val context: InstallContext =
    InstallContext(
      baseDirectory = baseDirectory,
      downloadDirectory = baseDirectory.getParent,
    )

  implicit protected val remoteRepoApi: PluginRepoUtils =
    new PluginRepoUtils

  implicit protected val localRegistry: LocalPluginRegistry =
    new LocalPluginRegistry(context)

  protected val ideaDependency: IdeaDependency = IdeaDependency(ideaBuildInfo)

  protected def dependencies: Seq[UnresolvedArtifact] =
    ideaDependency                                                               +:
      JbrDependency(baseDirectory, ideaBuildInfo, jbrInfo, Seq(ideaDependency))  +:
      plugins.map(pl => PluginDependency(pl, ideaBuildInfo, Seq(ideaDependency)))

  def update(): Unit = {
    topoSort(dependencies).foreach(update)

    val actualBuildNumber = context.productInfo.buildNumber
    val buildNumber = ideaBuildInfo.buildNumber
    if (buildNumber != actualBuildNumber) {
      log.warn(
        s"""### Installed build number is different form the original build number
           |### Installed : $actualBuildNumber
           |### Original  : $buildNumber
           |""".stripMargin.trim
      )
    }
  }

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
