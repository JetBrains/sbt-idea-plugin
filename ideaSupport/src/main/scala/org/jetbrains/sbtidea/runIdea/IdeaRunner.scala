package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.{PluginLogger, RuntimeDirectoryLog, RuntimePurpose}

import java.io.File
import java.nio.file.Path

class IdeaRunner(
  intellijBaseDirectory: Path,
  productInfoExtraDataProvider: ProductInfoExtraDataProvider,
  vmOptions: CustomIntellijVMOptions,
  vmOptionsBuilder: IntellijVMOptionsBuilder,
  blocking: Boolean,
  discardOutput: Boolean,
  programArguments: Seq[String] = Seq.empty
) extends IntellijAwareRunner(intellijBaseDirectory, blocking, discardOutput) {

  /**
   * For the version for IntelliJ Run Configurations see `buildTestVmOptionsString` in [[org.jetbrains.sbtidea.tasks.IdeaConfigBuilder]]
   * @return
   */
  override protected def buildJavaArgs: Seq[String] = prepareLaunch().arguments

  private[runIdea] def prepareLaunch(): IdeaRunner.PreparedLaunch = {
    val classpathOptions = Seq(
      "-cp", productInfoExtraDataProvider.bootClasspathJars.mkString(File.pathSeparator),
      IntellijVMOptionsBuilder.PathClassLoaderOption,
    )
    val configuredVmOptions = vmOptionsBuilder.build(
      vmOptions = vmOptions,
      forTests = false,
      quoteValues = false,
    ).filter(_.nonEmpty)

    IdeaRunner.PreparedLaunch(classpathOptions ++ configuredVmOptions, IdeaRunner.IdeaMainClass, programArguments)
  }

  private[sbtidea] def runWithRuntimeLogging(
    purpose: RuntimePurpose,
    project: String,
    logger: PluginLogger,
  ): Int = {
    val launch = prepareLaunch()
    runCommand(
      command = buildCommand(launch.arguments),
      beforeStart = () => {
        RuntimeDirectoryLog.report(purpose, project, launch.jvmOptions, logger)
      },
    )
  }
}

object IdeaRunner {
  private val IdeaMainClass = "com.intellij.idea.Main"

  /**
   * Keeps JVM options separate from application arguments until the command is assembled.
   */
  private[runIdea] final case class PreparedLaunch(
    jvmOptions: Seq[String],
    mainClass: String,
    programArguments: Seq[String],
  ) {
    def arguments: Seq[String] = jvmOptions ++ Seq(mainClass) ++ programArguments
  }
}
