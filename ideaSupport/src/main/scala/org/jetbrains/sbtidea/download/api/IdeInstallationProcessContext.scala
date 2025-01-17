package org.jetbrains.sbtidea.download.api

import org.jetbrains.sbtidea.productInfo.{ProductInfo, ProductInfoParser}
import sbt.pathToPathOps

import java.nio.file.Path

/**
 * Represents paths of ide which is already installed or that is in the process of installation
 *
 * @param baseDirectory represents a root directory for the ide installation (already installed or which is being installed)
 */
private[sbtidea] sealed class IdeInstallationContext(
  val baseDirectory: Path
) {
  lazy val productInfo: ProductInfo = {
    val productInfoFile = baseDirectory / "product-info.json"
    ProductInfoParser.parse(productInfoFile.toFile)
  }

  def pluginsDir: Path = baseDirectory / "plugins"
}

/**
 * Represents some paths used during the IDE installation process.<br>
 * This class is supposed to be mainly used in implementations of [[org.jetbrains.sbtidea.download.api.Installer]].
 *
 * @param artifactsDownloadsDir Directory where temporary artifacts are downloaded to before installing (usually .zip, .jar files)<br>
 *                              See [[org.jetbrains.sbtidea.Keys.artifactsDownloadsDir]]
 */
private[sbtidea] final class IdeInstallationProcessContext(
  baseDirectory: Path,
  val artifactsDownloadsDir: Path
) extends IdeInstallationContext(baseDirectory)