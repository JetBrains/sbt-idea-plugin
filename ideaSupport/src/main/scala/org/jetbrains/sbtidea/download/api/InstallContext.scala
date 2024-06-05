package org.jetbrains.sbtidea.download.api

import org.jetbrains.sbtidea.productInfo.{ProductInfo, ProductInfoParser}
import sbt.pathToPathOps

import java.nio.file.Path

case class InstallContext(baseDirectory: Path, downloadDirectory: Path) {
  lazy val productInfo: ProductInfo = {
    val productInfoFile = baseDirectory / "product-info.json"
    ProductInfoParser.parse(productInfoFile.toFile)
  }
}