package org.jetbrains.sbtidea.packaging.structure

import java.io.File

import org.jetbrains.sbtidea.packaging.PackagingKeys.ExcludeFilter._
import org.jetbrains.sbtidea.packaging.PackagingKeys.ShadePattern
import org.jetbrains.sbtidea.structure.{ModuleKey, ProjectNode}

trait ProjectPackagingOptions {
    def packageMethod: PackagingMethod
    def libraryMappings: Seq[(ModuleKey, Option[String])]
    def fileMappings: Seq[(File, String)]
    def shadePatterns: Seq[ShadePattern]
    def excludeFilter: ExcludeFilter
    def additionalProjects: Seq[PackagedProjectNode]
    def classRoots: Seq[File]
    def assembleLibraries: Boolean
}