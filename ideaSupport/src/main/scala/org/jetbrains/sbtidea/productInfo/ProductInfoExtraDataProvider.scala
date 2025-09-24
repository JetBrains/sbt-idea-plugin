package org.jetbrains.sbtidea.productInfo

import sbt.{IO, fileToRichFile}

import java.io.File
import java.nio.charset.StandardCharsets

trait ProductInfoExtraDataProvider {
  def vmOptionsAll: Seq[String]
  def bootClasspathJars: Seq[File]
  def productModulesJars: Seq[File]
  def testFrameworkJars: Seq[File]
}

/**
 * Calculates certain information based on `productInfo` with variables substituted with actual paths on a given machine.
 * For example if a path in `productInfo` contains `$$APP_PACKAGE`, it will be replaced with `intellijBaseDir`.
 *
 * @see [[org.jetbrains.sbtidea.productInfo.ProductInfo]]
 */
final class ProductInfoExtraDataProviderImpl(
  intellijBaseDir: File,
  productInfo: ProductInfo,
  launch: Launch
) extends ProductInfoExtraDataProvider {

  lazy val vmOptionsAll: Seq[String] = {
    val vmOptionsFile = intellijBaseDir / launch.vmOptionsFilePath
    val fromFile = IO.readLines(vmOptionsFile, StandardCharsets.UTF_8)
    val additional = launch.additionalJvmArguments
    val options = fromFile ++ additional
    val optionsSubstituted = options.map(substituteVariables)
    optionsSubstituted
  }

  /**
   * VM option can have a variable inside, for example {{{
   *   -Xbootclasspath/a:$APP_PACKAGE/lib/nio-fs.jar
   * }}}
   */
  private def substituteVariables(line: String): String =
    line
      .replace("%IDE_HOME%", intellijBaseDir.getPath) // Windows
      .replace("$IDE_HOME", intellijBaseDir.getPath) // Linux
      // NOTE: it should actually be IDE_HOME as well here on macOS as well.
      // $IDE_HOME ~ $APP_PACKAGE/Contents. But due to some historical reasons there is this mess with the variables.
      .replace("$APP_PACKAGE", intellijBaseDir.getPath) // macOS

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
