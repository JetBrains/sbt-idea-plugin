package org.jetbrains.sbtidea.packaging.structure

import java.io.File

import org.jetbrains.sbtidea.packaging.ExcludeFilter
import org.jetbrains.sbtidea.packaging.ShadePattern
import org.jetbrains.sbtidea.structure.ModuleKey

trait ProjectPackagingOptions {
    def packageMethod: PackagingMethod
    def libraryMappings: Seq[(ModuleKey, Option[String])]
    def libraryBaseDir: File
    def fileMappings: Seq[(File, String)]
    def shadePatterns: Seq[ShadePattern]
    def excludeFilter: ExcludeFilter
    def additionalProjects: Seq[PackagedProjectNode]
    def classRoots: Seq[File]
    def assembleLibraries: Boolean
}