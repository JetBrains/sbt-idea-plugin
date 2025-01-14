package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackageProjectData
import sbt.*
import sbt.Keys.*

object apiAdapter {

  def dumpDependencyStructureOffline = Def.task {
    SbtPackageProjectData(
      thisProject = thisProjectRef.value,
      thisProjectName = name.in(thisProjectRef).value,
      // note, we intentionally get the runtime classpath here
      cp = managedClasspath.in(Runtime).value,
      definedDeps = libraryDependencies.in(Compile).value,
      additionalProjects = packageAdditionalProjects.value,
      assembleLibraries = packageAssembleLibraries.value,
      productDirs = productDirectories.in(Compile).value,
      report = updateFull.value,
      libMapping = packageLibraryMappings.value,
      libraryBaseDir = packageLibraryBaseDir.value,
      additionalMappings = packageFileMappings.value,
      packageMethod = packageMethod.value,
      shadePatterns = shadePatterns.value,
      excludeFilter = pathExcludeFilter.value
    )
  }

  def dumpDependencyStructure = Def.task {
    SbtPackageProjectData(
      thisProject = thisProjectRef.value,
      thisProjectName = name.in(thisProjectRef).value,
      // note, we intentionally get the runtime classpath here
      cp = managedClasspath.in(Runtime).value,
      definedDeps = libraryDependencies.in(Compile).value,
      additionalProjects = packageAdditionalProjects.value,
      assembleLibraries = packageAssembleLibraries.value,
      productDirs = products.in(Compile).value,
      report = updateFull.value,
      libMapping = packageLibraryMappings.value,
      libraryBaseDir = packageLibraryBaseDir.value,
      additionalMappings = packageFileMappings.value,
      packageMethod = packageMethod.value,
      shadePatterns = shadePatterns.value,
      excludeFilter = pathExcludeFilter.value
    )
  }

}
