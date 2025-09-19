package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider

import java.io.File
import java.nio.file.Path
import scala.collection.mutable

class IdeaRunner(
  intellijBaseDirectory: Path,
  productInfoExtraDataProvider: ProductInfoExtraDataProvider,
  vmOptions: IntellijVMOptionsBuilder.VmOptions,
  vmOptionsBuilder: IntellijVMOptionsBuilder,
  blocking: Boolean,
  discardOutput: Boolean,
  programArguments: Seq[String] = Seq.empty
) extends IntellijAwareRunner(intellijBaseDirectory, blocking, discardOutput) {

  /**
   * For the version for IntelliJ Run Configurations see `buildTestVmOptionsString` in [[org.jetbrains.sbtidea.tasks.IdeaConfigBuilder]]
   * @return
   */
  override protected def buildJavaArgs: Seq[String] = {
    val builder = mutable.ArrayBuffer[String]()

    //vm options
    builder ++= List("-cp", productInfoExtraDataProvider.bootClasspathJars.mkString(File.pathSeparator))
    builder += IntellijVMOptions.USE_PATH_CLASS_LOADER
    builder ++= vmOptionsBuilder.build(
      vmOptions = vmOptions,
      forTests = false,
      quoteValues = false,
    ).filter(_.nonEmpty)
    //main
    builder += IdeaRunner.IdeaMainClass
    //arguments
    builder ++= programArguments

    builder.toList
  }
}

object IdeaRunner {
  private val IdeaMainClass = "com.intellij.idea.Main"
}