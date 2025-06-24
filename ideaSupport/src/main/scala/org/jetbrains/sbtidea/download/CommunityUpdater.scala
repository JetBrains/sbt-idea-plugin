package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.download.cachesCleanup.{OldDownloadsCleanup, OldSdkCleanup}
import org.jetbrains.sbtidea.download.idea.IdeaDependency
import org.jetbrains.sbtidea.download.jbr.JbrDependency
import org.jetbrains.sbtidea.download.plugin.{LocalPluginRegistry, PluginDependency, PluginRepoUtils}
import org.jetbrains.sbtidea.{IntellijPlugin, JbrInfo, PluginLogger as log}

import java.nio.file.{Files, Path}

class CommunityUpdater(
  baseDirectory: Path,
  artifactsDownloadsDirectory: Path,
  ideaBuildInfo: BuildInfo,
  jbrInfo: JbrInfo,
  plugins: Seq[IntellijPlugin],
  autoRemoveOldIntellijSdk: Boolean,
  autoRemoveOldDownloads: Boolean
) {

  @deprecated("Use the other constructor", "4.1.1")
  def this(
    baseDirectory: Path,
    ideaBuildInfo: BuildInfo,
    jbrInfo: JbrInfo,
    plugins: Seq[IntellijPlugin],
    //noinspection ScalaUnusedSymbol
    withSources_Ignored: Boolean = true
  ) = {
    this(
      baseDirectory,
      baseDirectory.resolve("../downloads"),
      ideaBuildInfo,
      jbrInfo,
      plugins,
      autoRemoveOldIntellijSdk = false,
      autoRemoveOldDownloads = false
    )
  }

  @deprecated("Use the other constructor", "4.1.7")
  def this(
    baseDirectory: Path,
    artifactsDownloadsDirectory: Path,
    ideaBuildInfo: BuildInfo,
    jbrInfo: JbrInfo,
    plugins: Seq[IntellijPlugin]
  ) = {
    this(
      baseDirectory,
      artifactsDownloadsDirectory,
      ideaBuildInfo,
      jbrInfo,
      plugins,
      autoRemoveOldIntellijSdk = false,
      autoRemoveOldDownloads = false
    )
  }

  @deprecated("Use the other constructor", "4.1.14")
  def this(
    baseDirectory: Path,
    artifactsDownloadsDirectory: Path,
    ideaBuildInfo: BuildInfo,
    jbrInfo: JbrInfo,
    plugins: Seq[IntellijPlugin],
    autoRemoveOldIntellijSdk: Boolean,
  ) = {
    this(
      baseDirectory,
      artifactsDownloadsDirectory,
      ideaBuildInfo,
      jbrInfo,
      plugins,
      autoRemoveOldIntellijSdk = autoRemoveOldIntellijSdk,
      autoRemoveOldDownloads = false
    )
  }

  implicit protected val context: IdeInstallationProcessContext =
    new IdeInstallationProcessContext(
      baseDirectory = baseDirectory,
      artifactsDownloadsDir = artifactsDownloadsDirectory
    )

  implicit protected val remoteRepoApi: PluginRepoUtils =
    new PluginRepoUtils

  implicit protected val localRegistry: LocalPluginRegistry =
    new LocalPluginRegistry(context)

  protected val ideaDependency: IdeaDependency = IdeaDependency(ideaBuildInfo)

  protected def dependencies: Seq[UnresolvedArtifact] =
    ideaDependency +:
      JbrDependency(baseDirectory, ideaBuildInfo, jbrInfo, Seq(ideaDependency)) +:
      plugins.map(pl => PluginDependency(pl, ideaBuildInfo, Seq(ideaDependency)))

  def update(): Unit = {
    // Example: "~/.ScalaPluginIC/sdk"
    // (see org.jetbrains.sbtidea.Init.buildSettings)
    // NOTE: it's a dirty implicit dependency on the logic in buildSettings, but should be enough in practice
    // I want to avoid introducing extra keys or parameters. We can change it at any time if needed.
    val sdksRootDir = baseDirectory.getParent
    if (Files.isDirectory(sdksRootDir)) {
      new OldSdkCleanup(log).detectOldSdksRemoveIfNeeded(sdksRootDir, autoRemove = autoRemoveOldIntellijSdk)
    }

    // Clean up old downloads in the artifact downloads directory
    if (Files.isDirectory(artifactsDownloadsDirectory)) {
      new OldDownloadsCleanup(log).detectOldDownloadsRemoveIfNeeded(artifactsDownloadsDirectory, autoRemove = autoRemoveOldDownloads)
    }

    val dependenciesSorted = topoSort(dependencies)
    dependenciesSorted.foreach(update)

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

  private def update(dependency: UnresolvedArtifact): Unit = {
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

    nonInstalled.foreach(_.install())
  }

  private[download] def topoSort(deps: Seq[UnresolvedArtifact]): Seq[UnresolvedArtifact] = {
    val indexed = topoSortImpl(deps)
    indexed
      .sortBy(-_._2)
      .map(_._1)
      .distinct
  }

  private def topoSortImpl(deps: Seq[UnresolvedArtifact], level: Int = 0): Seq[(UnresolvedArtifact, Int)] = {
    deps.map(_ -> level) ++ deps.flatMap(dep => topoSortImpl(dep.dependsOn, level + 1))
  }
}
