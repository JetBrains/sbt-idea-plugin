package org.jetbrains.sbtidea

import scala.annotation.nowarn

@nowarn("cat=deprecation")
case class IdeaConfigBuildingOptions(generateDefaultRunConfig: Boolean = true,
                                     generateJUnitTemplate: Boolean = true,
                                     @deprecated("This value is unused and will be deleted in future releases. In IntelliJ IDEA 2023.1 `-Didea.ProcessCanceledException` VM options is dropped (for details see https://youtrack.jetbrains.com/issue/IDEA-304945)")
                                     generateNoPCEConfiguration: Boolean = false,
                                     programParams: String = "",
                                     ideaRunEnv: Map[String, String] = Map.empty,
                                     ideaTestEnv: Map[String, String] = Map.empty,
                                     testModuleName: String = "",
                                     workingDir: String = "$PROJECT_DIR$/",
                                     testSearchScope: String = "moduleWithDependencies")