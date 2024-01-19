package org.jetbrains.sbtidea

/**
  * type aliases and val aliases for sbt autoImport
  * all of the types mentioned in PackagingKeys aka exposed to the user should be aliased here to avoid the
  * necessity of explicit importing
  */
//noinspection ScalaUnusedSymbol (can be used by sbt plugin users)
trait Defns {

  type IdeaConfigBuildingOptions = org.jetbrains.sbtidea.IdeaConfigBuildingOptions
  final val IdeaConfigBuildingOptions: org.jetbrains.sbtidea.IdeaConfigBuildingOptions.type = org.jetbrains.sbtidea.IdeaConfigBuildingOptions

  type IntellijVMOptions = org.jetbrains.sbtidea.runIdea.IntellijVMOptions
  final val IntellijVMOptions: org.jetbrains.sbtidea.runIdea.IntellijVMOptions.type = org.jetbrains.sbtidea.runIdea.IntellijVMOptions

  type pluginXmlOptions = org.jetbrains.sbtidea.pluginXmlOptions
  final val pluginXmlOptions: org.jetbrains.sbtidea.pluginXmlOptions.type = org.jetbrains.sbtidea.pluginXmlOptions

  type IntellijPlugin = org.jetbrains.sbtidea.IntellijPlugin
  final val intellijPlugin: org.jetbrains.sbtidea.IntellijPlugin.type = org.jetbrains.sbtidea.IntellijPlugin

  type IntelliJPlatform = org.jetbrains.sbtidea.IntelliJPlatform
  final val IntelliJPlatform: org.jetbrains.sbtidea.IntelliJPlatform.type = org.jetbrains.sbtidea.IntelliJPlatform

  type JbrInfo = org.jetbrains.sbtidea.JbrInfo

  type JBR = org.jetbrains.sbtidea.JBR
  final val JBR: org.jetbrains.sbtidea.JBR.type = org.jetbrains.sbtidea.JBR

  type AutoJbr = org.jetbrains.sbtidea.AutoJbr
  final val AutoJbr: org.jetbrains.sbtidea.AutoJbr.type = org.jetbrains.sbtidea.AutoJbr

  type NoJbr = org.jetbrains.sbtidea.NoJbr.type
  final val NoJbr = org.jetbrains.sbtidea.NoJbr

  type PluginVerifierOptions = org.jetbrains.sbtidea.verifier.PluginVerifierOptions
  final val PluginVerifierOptions = org.jetbrains.sbtidea.verifier.PluginVerifierOptions

  type PluginSigningOptions = org.jetbrains.sbtidea.PluginSigningOptions
  final val PluginSigningOptions = org.jetbrains.sbtidea.PluginSigningOptions
}
