package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackageProjectData
import sbt._
import sbt.Keys._

object apiAdapter {

  def dumpDependencyStructureOffline = Def.task {
    SbtPackageProjectData(
      thisProjectRef.value,
      managedClasspath.in(Compile).value,
      libraryDependencies.in(Compile).value,
      packageAdditionalProjects.value,
      packageAssembleLibraries.value,
      productDirectories.in(Compile).value,
      updateFull.value,
      packageLibraryMappings.value,
      packageLibraryBaseDir.value,
      packageFileMappings.value,
      packageMethod.value,
      shadePatterns.value,
      pathExcludeFilter.value
    )
  }

  def dumpDependencyStructure = Def.task {
    SbtPackageProjectData(
      thisProjectRef.value,
      managedClasspath.in(Compile).value,
      libraryDependencies.in(Compile).value,
      packageAdditionalProjects.value,
      packageAssembleLibraries.value,
      products.in(Compile).value,
      updateFull.value,
      packageLibraryMappings.value,
      packageLibraryBaseDir.value,
      packageFileMappings.value,
      packageMethod.value,
      shadePatterns.value,
      pathExcludeFilter.value
    )
  }

}
