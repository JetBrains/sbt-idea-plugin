package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.runIdea.CustomIntellijVMOptions.DebugInfo

import scala.annotation.nowarn

/**
 * The class represents VM options that will be merged (appended or override) with default IDE options.
 * The default IDE options from the `idea.vmoptions` file from the IntelliJ SDK installation root
 * (for example, from `~/.ScalaPluginIC/sdk/252.26199.7/bin/mac/idea.vmoptions`).
 *
 * The construction of the final VM options list (passed to the dev IDE process or in tests) is done in:
 * [[org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder]]
 *
 * @param xmx          if set, it will override the default value of -Xmx
 * @param xms          if set, it will override the default value of -Xms
 * @param debugInfo    if set, a debug agent will be attached to the IDE process with given parameters
 * @param extraOptions these options will be appended to the list of VM standard options
 *                     (from the idea.vmoptions file or those added inside [[IntellijVMOptionsBuilder]])<br>
 *                     Note `extraOptions` don't replace any existing options, they are simply appended
 */
@nowarn("cat=deprecation")
case class CustomIntellijVMOptions(
  xmx: Option[Int] = None,
  xms: Option[Int] = None,
  debugInfo: Option[DebugInfo] = Some(DebugInfo.Default),
  extraOptions: Seq[String] = Nil
) {
  def withExtraOption(option: String): CustomIntellijVMOptions = withExtraOptions(Seq(option))
  def withExtraOptions(options: Seq[String]): CustomIntellijVMOptions = copy(extraOptions = extraOptions ++ options)
}

object CustomIntellijVMOptions {
  case class DebugInfo(port: Int, suspend: Boolean)
  object DebugInfo {
    val Default = DebugInfo(port = 5005, suspend = false)
  }
}