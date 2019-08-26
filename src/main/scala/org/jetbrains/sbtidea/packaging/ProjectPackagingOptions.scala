package org.jetbrains.sbtidea.packaging

import java.io.File

import org.jetbrains.sbtidea.Keys.ShadePattern
import org.jetbrains.sbtidea.packaging.artifact.ExcludeFilter.ExcludeFilter
import org.jetbrains.sbtidea.structure.{ModuleKey, ProjectNode}

trait ProjectPackagingOptions {
    def packageMethod: PackagingMethod
    def libraryMappings: Seq[(ModuleKey, Option[String])]
    def fileMappings: Seq[(File, String)]
    def shadePatterns: Seq[ShadePattern]
    def excludeFilter: ExcludeFilter
    def additionalProjects: Seq[ProjectNode]
    def classRoots: Seq[File]
    def assembleLibraries: Boolean
}