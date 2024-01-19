package org.jetbrains.sbtidea

import scala.annotation.nowarn

@nowarn("cat=deprecation")
case class IdeaConfigBuildingOptions(generateDefaultRunConfig: Boolean = true,
                                     generateJUnitTemplate: Boolean = true,
                                     programParams: String = "",
                                     ideaRunEnv: Map[String, String] = Map.empty,
                                     ideaTestEnv: Map[String, String] = Map.empty,
                                     testModuleName: String = "",
                                     workingDir: String = "$PROJECT_DIR$/",
                                     testSearchScope: String = "moduleWithDependencies")