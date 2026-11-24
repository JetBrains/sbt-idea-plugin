package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.download.api.IdeInstallationContext
import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.runIdea.CustomIntellijVMOptions.DebugInfo
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.VmOptions

import java.nio.file.Path
import scala.collection.mutable

/**
 * The class is responsible for constructing the list of VM options passed to the dev IDE process or to tests.
 * The class is not meant to be customizable by users directly.
 * Any customization comes from the class parameters (see keys in [[org.jetbrains.sbtidea.Keys]]).
 *
 * Note it doesn't add the classpath VM option - the classpath in other places:
 * [[org.jetbrains.sbtidea.tasks.IdeaConfigBuilder]]
 * or [[org.jetbrains.sbtidea.tasks.classpath.TestClasspathTasks.fullTestClasspathForSbt]]
 *
 * @param pluginPath                     path to the directory in which the current plugin is assembled (usually `target/plugin/PluginName`
 * @param ideaHome                       this path will be used as idea home when you run IDEA or unit tests<br>
 *                                       example: {{{ <userHome>/.ScalaPluginIU }}}
 * @param intellijDirectory              example: {{{ <userHome>/.ScalaPluginIU/sdk/223.6160 }}}
 * @param customSystemAndConfigDirPrefix if specified, the system and config directories will have it as prefix:
 *                                        - -Didea.system.path=ideaHome/$$customSystemAndConfigDirPrefix-system
 *                                        - -Didea.config.path==ideaHome/$$customSystemAndConfigDirPrefix-config
 *
 * @note usnig "case" class primarily to be able to copy the class
 */
final case class IntellijVMOptionsBuilder(
  platform: IntelliJPlatform,
  productInfoExtraDataProvider: ProductInfoExtraDataProvider,
  pluginPath: Path,
  ideaHome: Path,
  intellijDirectory: Path,
  customSystemAndConfigDirPrefix: Option[String] = None,
  testRuntimeDirectories: Option[IntellijTestRuntimeDirectories.RuntimeDirectories] = None,
  devRuntimeDirectories: Option[IntellijTestRuntimeDirectories.RuntimeDirectories] = None,
) {

  def withRuntimeDirectories(directories: IntellijTestRuntimeDirectories.RuntimeDirectories): IntellijVMOptionsBuilder =
    copy(devRuntimeDirectories = Some(directories))

  def withCustomSystemAndConfigDir(dirsPrefix: String): IntellijVMOptionsBuilder =
    copy(customSystemAndConfigDirPrefix = Some(dirsPrefix))

  def build(
    vmOptions: VmOptions,
    forTests: Boolean,
    quoteValues: Boolean,
  ): Seq[String] = buildImpl(
    vmOptions = vmOptions,
    forTests = forTests,
    quoteValues = quoteValues,
    escapeXml = true,
  ).filter(_.nonEmpty)

  def buildQuotedNoEscapeXml(
    vmOptions: VmOptions,
    forTests: Boolean,
  ): Seq[String] = buildImpl(
    vmOptions = vmOptions,
    forTests = forTests,
    quoteValues = true,
    escapeXml = false
  ).filter(_.nonEmpty)

  private def buildImpl(
    vmOptions: VmOptions,
    forTests: Boolean,
    quoteValues: Boolean,
    escapeXml: Boolean,
  ): Seq[String] = {
    val options = vmOptions match {
      case VmOptions.New(options) =>
        buildNew(options, quoteValues, escapeXml, forTests)
    }
    val selectedDirectories = if (forTests) testRuntimeDirectories else devRuntimeDirectories
    if (selectedDirectories.nonEmpty) {
      val directoryOptions = selectedDirectories.get.jvmOptions.map { option =>
        val Array(key, value) = option.split("=", 2)
        val quotedValue =
          if (!quoteValues) value
          else if (escapeXml) value.xmlQuote
          else "\"" + value + "\""
        s"$key=$quotedValue"
      }
      options.filterNot(IntellijTestRuntimeDirectories.isIntellijDirectoryOption) ++ directoryOptions
    } else options
  }

  private def buildNew(
    options: CustomIntellijVMOptions,
    quoteValues: Boolean,
    escapeXml: Boolean,
    forTests: Boolean
  ): Seq[String] = {
    def escapeQuotes(line: String): String =
      if (escapeXml) line.replace("\"", "&quot;")
      else line

    // Escaping quotes. Example: `-Djdk.http.auth.tunneling.disabledSchemes=""`
    val defaultOptionsFromProductInfoOriginal =
      productInfoExtraDataProvider.vmOptionsAll.map(escapeQuotes)

    val defaultOptionsFromProductInfoFiltered = if (forTests)
      defaultOptionsFromProductInfoOriginal.filterNot { optionLine =>
        // By default, the additional VM options contain these options that should not be in tests
        //  -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader
        optionLine.startsWith("-Djava.system.class.loader=")
      }
    else
      defaultOptionsFromProductInfoOriginal

    val extraOptions = mutable.ArrayBuffer[String]()

    def qq(str: String): String =
      if (!quoteValues)
        str
      else if (escapeXml)
        str.xmlQuote
      else
        s""""$str""""

    options.debugInfo match {
      case Some(DebugInfo(debugPort, suspend)) =>
        val suspendValue = if (suspend) "y" else "n"
        extraOptions += s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendValue,address=$debugPort"
      case _ =>
    }

    if (!forTests) {
      extraOptions += "-Didea.is.internal=true"
    }

    extraOptions += s"-Dplugin.path=${qq(pluginPath.toString)}"

    val (systemDirName, configDirName) = customSystemAndConfigDirPrefix match {
      case Some(prefix) =>
        (s"$prefix-system", s"$prefix-config")
      case None =>
        if (forTests) ("test-system", "test-config")
        else ("system", "config")
    }
    val (systemPath, configPath) = (ideaHome.resolve(systemDirName), ideaHome.resolve(configDirName))

    val customPluginsPath = intellijDirectory.resolve(IdeInstallationContext.customPluginsDirName)
    val logPath = systemPath.resolve("log")

    // IntelliJ requires all these paths to be set when "idea.paths.selector" VM options is used (that comes from product-info.json)
    // See com.intellij.idea.SystemHealthMonitor#checkIdeDirectories
    extraOptions += s"-Didea.system.path=${qq(systemPath.toString)}"
    extraOptions += s"-Didea.config.path=${qq(configPath.toString)}"
    extraOptions += s"-Didea.plugins.path=${qq(customPluginsPath.toString)}"
    extraOptions += s"-Didea.log.path=${qq(logPath.toString)}"

    //TODO: extra plugins options org.jetbrains.intellij.platform.gradle.tasks.Registrable?
    // Note: these VM options are not present in the product-info.json but they were present in old default options.
    // It seems they are not required, but also it seems they don't hurt.
    // Let's review them 1 by 1 and decide if they are needed
    extraOptions ++= Seq(
      // Disable platform updates in Dev IDEA.
      // In Dev IDEA we use the version specified in sbt build definition via the `intellijBuild` setting.
      // see com.intellij.openapi.updateSettings.impl.ExternalUpdateManager#ExternalUpdateManager
      // see org.jetbrains.plugins.scala.components.ScalaPluginUpdater.suggestIdeaUpdate
      "-Dide.no.platform.update=true",

      // Not required, but won't hurt. Originally added by Michael in revision 174ce88a.
      "-Didea.initially.ask.config=true",
    )

    val platformPrefix = platform.platformPrefix
    if (platformPrefix.nonEmpty) {
      // Not sure what this platform prefix is needed for
      extraOptions += s"-Didea.platform.prefix=$platformPrefix"
    }

    if (forTests) {
      extraOptions += "-Didea.use.core.classloader.for.plugin.path=true"
      extraOptions += "-Didea.force.use.core.classloader=true"
      // Note: Gradle plugins pass some extra VM options in org.jetbrains.intellij.platform.gradle.tasks.TestIdeTask:
      // https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/tasks/RunIdeTask.kt#L68
      // But it seems like they are not mandatory, and we are not that sure we want/need to add them as well.
    }

    // Set log level to warning explicitly, as 262+ IDEs disable it by default
    extraOptions += "-Dintellij.console.log.level=warning"

    options.xmx.foreach(v => extraOptions += s"-Xmx${v}m")
    options.xms.foreach(v => extraOptions += s"-Xms${v}m")
    extraOptions ++= options.extraOptions

    // NOTE: first go the default options, then extra options
    // Some extra options can potentially override the default (it depends on the concrete option, e.g. it can work for -Xmx)
    defaultOptionsFromProductInfoFiltered ++ extraOptions
  }
}

private[sbtidea]
object IntellijVMOptionsBuilder {

  val PathClassLoaderOption = "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader"

  sealed trait VmOptions {
    def withExtraOption(option: String): VmOptions = this match {
      case VmOptions.New(extraOption) => VmOptions.New(extraOption.withExtraOption(option))
    }
    def withExtraOptions(extraOptions: Seq[String]): VmOptions = this match {
      case VmOptions.New(options) => VmOptions.New(options.withExtraOptions(extraOptions))
    }
  }
  object VmOptions {
    case class New(options: CustomIntellijVMOptions) extends VmOptions
  }
}
