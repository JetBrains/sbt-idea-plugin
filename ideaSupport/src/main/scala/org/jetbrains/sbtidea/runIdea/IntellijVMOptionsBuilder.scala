package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.download.{IntelliJVersionDetector, Version}
import org.jetbrains.sbtidea.productInfo.ProductInfoExtraDataProvider
import org.jetbrains.sbtidea.runIdea.CustomIntellijVMOptions.DebugInfo
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.{VmOptions, buildOld}
import sbt.pathToPathOps

import java.nio.file.Path
import scala.annotation.nowarn
import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered

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
) {

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
    vmOptions match {
      case VmOptions.New(options) =>
        buildNew(options, quoteValues, escapeXml, forTests)
      case VmOptions.Old(options) =>
        buildOld(options, quoteValues, escapeXml, forTests)
    }
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

    val customPluginsPath = systemPath.resolve("plugins")
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

  private def buildOld(
    @nowarn("cat=deprecation")
    options: IntellijVMOptions,
    quoteValues: Boolean,
    escapeXml: Boolean,
    forTests: Boolean
  ): Seq[String] = {
    import options.*

    val intellijVersion = IntelliJVersionDetector.detectIntellijVersion(intellijDirectory.toFile)

    def OQ(str: String): String =
      if (quoteValues) {
        if (escapeXml) str.xmlQuote else "\"" + str + "\""
      } else str

    val buffer = new mutable.ArrayBuffer[String]()
    buffer ++= defaultOptions

    val jnaFolderName = System.getProperty("os.arch") match {
      case "aarch64" => "aarch64"
      case _ => "amd64" //currently there are only two possible folders in `lib/jna`
    }

    //if the version is not detected, assume as if it's the latest
    if (intellijVersion.forall(_ > Version("223.6160"))) {
      val pty4jFolderPath = (intellijDirectory / "lib/pty4j").toString.replace("\\", "/")
      val jnaFolderPath = (intellijDirectory / "lib/jna" / jnaFolderName).toString.replace("\\", "/")
      buffer += s"-Dpty4j.preferred.native.folder=${OQ(pty4jFolderPath)}"
      buffer += s"-Djna.boot.library.path=${OQ(jnaFolderPath)}"
      buffer += s"-Djna.nounpack=true"
      buffer += s"-Djna.nosys=true"
    }

    buffer += s"-Xms${xms}m"
    buffer += s"-Xmx${xmx}m"
    buffer += s"-XX:ReservedCodeCacheSize=${reservedCodeCacheSize}m"
    buffer += s"-XX:SoftRefLRUPolicyMSPerMB=$softRefLRUPolicyMSPerMB"
    buffer += gc
    buffer += gcOpt
    val (system, config) =
      if (forTests) (ideaHome.resolve("test-system"), ideaHome.resolve("test-config"))
      else (ideaHome.resolve("system"), ideaHome.resolve("config"))
    buffer += s"-Didea.system.path=${OQ(system.toString)}"
    buffer += s"-Didea.config.path=${OQ(config.toString)}"
    buffer += s"-Dplugin.path=${OQ(pluginPath.toString)}"
    if (forTests) {
      buffer += "-Didea.use.core.classloader.for.plugin.path=true"
      buffer += "-Didea.force.use.core.classloader=true"
    }
    if (!forTests)
      buffer += "-Didea.is.internal=true"
    if (debug) {
      val suspendValue = if (suspend) "y" else "n"
      buffer += s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendValue,address=$debugPort"
    }
    val platformPrefix = platform.platformPrefix
    if (platformPrefix.nonEmpty)
      buffer += s"-Didea.platform.prefix=$platformPrefix"
    buffer
  }

  /**
   * TODO: remove this temporary wrapper when the deprecated [[IntellijVMOptions]] is not dropped completely
   */
  sealed trait VmOptions {
    def withExtraOption(option: String): VmOptions = this match {
      case VmOptions.New(extraOption) => VmOptions.New(extraOption.withExtraOption(option))
      case VmOptions.Old(extraOption) => VmOptions.Old(extraOption.withOption(option))
    }
    def withExtraOptions(extraOptions: Seq[String]): VmOptions = this match {
      case VmOptions.New(options) => VmOptions.New(options.withExtraOptions(extraOptions))
      case VmOptions.Old(options) => VmOptions.Old(options.withOptions(extraOptions))
    }
  }
  object VmOptions {
    case class New(options: CustomIntellijVMOptions) extends VmOptions
    @nowarn("cat=deprecation")
    case class Old(options: IntellijVMOptions) extends VmOptions
  }
}
