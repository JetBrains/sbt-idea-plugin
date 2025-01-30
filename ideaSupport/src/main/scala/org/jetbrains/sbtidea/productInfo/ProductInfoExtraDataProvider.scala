package org.jetbrains.sbtidea.productInfo

import sbt.fileToRichFile

import java.io.File

class ProductInfoExtraDataProvider(
  intellijBaseDir: File,
  productInfo: ProductInfo,
  launch: Launch
) {

  lazy val bootClasspathJars: Seq[File] =
    (launch.bootClassPathJarNames :+ "nio-fs.jar") // TODO(SCL-23540): use launch.additionalJvmArguments instead of hardcoded value
      .distinct
      .map(jarName => intellijBaseDir / "lib" / jarName)

  lazy val productModulesJars: Seq[File] = {
    val relativePaths = productInfo.productModulesLayout.flatMap(_.classPath.toSeq.flatten)
    relativePaths.map(intellijBaseDir / _)
  }

  lazy val testFrameworkJars: Seq[File] =
    Seq(intellijBaseDir / "lib" / "testFramework.jar")
}
