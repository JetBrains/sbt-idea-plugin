package org.jetbrains.sbtidea.productInfo

import sbt.fileToRichFile

import java.io.File

class ProductInfoExtraDataProvider(
  intellijBaseDir: File,
  productInfo: ProductInfo,
  launch: Launch
) {

  lazy val bootClasspathJars: Seq[File] =
    launch.bootClassPathJarNames.map(jarName => intellijBaseDir / "lib" / jarName)

  lazy val productModulesJars: Seq[File] = {
    val relativePaths = productInfo.productModulesLayout.flatMap(_.classPath.toSeq.flatten)
    relativePaths.map(intellijBaseDir / _)
  }

  lazy val testFrameworkJars: Seq[File] =
    Seq(intellijBaseDir / "lib" / "testFramework.jar")
}
