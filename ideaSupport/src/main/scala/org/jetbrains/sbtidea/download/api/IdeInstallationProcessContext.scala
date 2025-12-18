package org.jetbrains.sbtidea.download.api

import org.jetbrains.sbtidea.download.api.IdeInstallationContext.customPluginsDirName
import org.jetbrains.sbtidea.productInfo.{ProductInfo, ProductInfoParser}
import sbt.pathToPathOps

import java.nio.file.Path

/**
 * Represents paths of ide which is already installed or that is in the process of installation
 *
 * @param baseDirectory represents a root directory for the ide installation (already installed or which is being installed)
 */
sealed class IdeInstallationContext(
  val baseDirectory: Path
) {
  lazy val productInfo: ProductInfo = {
    val productInfoFile = baseDirectory / "product-info.json"
    ProductInfoParser.parse(productInfoFile.toFile)
  }

  /**
   * Represents the path to the "custom" plugins:
   *   - installed via Marketplace;
   *   - installed via "Install Plugin from Disk";
   *   - plugins from [[org.jetbrains.sbtidea.Keys.intellijPlugins]], except bundled;
   *   - a plugin from `org.jetbrains.sbtidea.packaging.PackagingKeys.packageOutputDir` when running locally;
   *   - updates for bundled plugins.
   *
   * Passed to the IDE via `-Didea.plugins.path` option, see [[org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.build]]
   */
  def pluginsDir: Path = baseDirectory / customPluginsDirName
}

object IdeInstallationContext {
  val customPluginsDirName: String = "custom-plugins"
}

/**
 * Represents some paths used during the IDE installation process.<br>
 * This class is supposed to be mainly used in implementations of [[org.jetbrains.sbtidea.download.api.Installer]].
 *
 * @param artifactsDownloadsDir Directory where temporary artifacts are downloaded to before installing (usually .zip, .jar files)<br>
 *                              See [[org.jetbrains.sbtidea.Keys.artifactsDownloadsDir]]
 */
final class IdeInstallationProcessContext(
  baseDirectory: Path,
  val artifactsDownloadsDir: Path
) extends IdeInstallationContext(baseDirectory)
