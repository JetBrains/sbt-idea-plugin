package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.PackagingKeys.*
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackageProjectData
import sbt.{Def, *}
import sbt.Keys.*

object apiAdapter {

  def dumpDependencyStructureOffline: Def.Initialize[Task[SbtPackageProjectData]] = dumpDependencyStructure

  def dumpDependencyStructure: Def.Initialize[Task[SbtPackageProjectData]] = Def.task {
    SbtPackageProjectData(
      thisProjectRef.value,
      name.in(thisProjectRef).value,
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
