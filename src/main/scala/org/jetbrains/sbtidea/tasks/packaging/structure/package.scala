package org.jetbrains.sbtidea.tasks.packaging

import java.io.File

import org.jetbrains.sbtidea.tasks.packaging.artifact.ExcludeFilter.ExcludeFilter
import org.jetbrains.sbtidea.tasks.packaging.artifact.ShadePattern

package object structure {
//  type Project
//  type ProjectBuildMetadata

  trait ModuleKey {
    def ~==(other: ModuleKey): Boolean
    def org: String
    def name: String
    def revision: String
  }

  trait ProjectPackagingOptions {
    def packageMethod: PackagingMethod
    def libraryMappings: Seq[(ModuleKey, Option[String])]
    def fileMappings: Seq[(File, String)]
    def shadePatterns: Seq[ShadePattern]
    def excludeFilter: ExcludeFilter
    def additionalProjects: Seq[ProjectNode]
  }

  trait ProjectNode {
    def parents: Seq[ProjectNode]
    def children: Seq[ProjectNode]
    def libs: Seq[Library]
    def packagingOptions: ProjectPackagingOptions
  }

  trait Library {
    def key: ModuleKey
    def jarFile: File
  }
}
