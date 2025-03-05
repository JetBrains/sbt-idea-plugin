package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.IdeaConfigBuildingOptions.AdditionalRunConfigData

import scala.annotation.nowarn

@nowarn("cat=deprecation")
case class IdeaConfigBuildingOptions(
  generateDefaultRunConfig: Boolean = true,
  additionalRunConfigs: Seq[AdditionalRunConfigData] = Seq.empty,
  generateJUnitTemplate: Boolean = true,
  programParams: String = "",
  ideaRunEnv: Map[String, String] = Map.empty,
  ideaTestEnv: Map[String, String] = Map.empty,
  testModuleName: String = "",
  workingDir: String = "$PROJECT_DIR$/",
  testSearchScope: String = "moduleWithDependencies"
)

object IdeaConfigBuildingOptions {
  /**
   * Describes information required to create addition run configuration.
   * These configurations should be almost the same as the default run configuration with some additional parameters
   *
   * @param configurationNameSuffix the suffix that will be added to the default run configuration name.<br>
   *                                Examples: {{{
   *                                  default configuration name: "scalaCommunity"
   *                                  suffix: "-FUS"
   *                                  additional configuration name: "scalaCommunity-FUS"
   *                                  suffix: " (split mode)"
   *                                  additional configuration name: "scalaCommunity (split mode)"
   *                                }}}
   * @param extraVmOptions          additional vm options that will be appended on top of the default vm options
   */
  case class AdditionalRunConfigData(
    configurationNameSuffix: String,
    extraVmOptions: Seq[String]
  )
}