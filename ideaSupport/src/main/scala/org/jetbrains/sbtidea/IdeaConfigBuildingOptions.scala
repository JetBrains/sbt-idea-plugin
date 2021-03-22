package org.jetbrains.sbtidea

case class IdeaConfigBuildingOptions(generateDefaultRunConfig: Boolean = true,
                                       generateJUnitTemplate: Boolean = true,
                                       generateNoPCEConfiguration: Boolean = false,
                                       programParams: String = "",
                                       ideaRunEnv: Map[String, String] = Map.empty,
                                       ideaTestEnv: Map[String, String] = Map.empty,
                                       testModuleName: String = "",
                                       workingDir: String = "$PROJECT_DIR$/",
                                       testSearchScope: String = "moduleWithDependencies")