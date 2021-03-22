package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackageProjectData
import org.jetbrains.sbtidea.structure.ModuleKey
import sbt._

object PackagingKeys extends PackagingKeysInit with PackagingDefs {

  /* Settings */

  lazy val packageMethod = settingKey[PackagingMethod](
    "What kind of artifact to produce from given project")

  lazy val packageAdditionalProjects = settingKey[Seq[Project]](
    "Projects to package alongside current, without adding classpath dependencies")
  
  lazy val packageLibraryMappings = settingKey[Seq[(ModuleID, Option[String])]](
    "Overrides for library mappings in artifact")

  lazy val packageLibraryBaseDir = settingKey[File](
    "Directory to place library dependencies into. *Relative* to the artifact output dir"
  )
  
  lazy val packageFileMappings = settingKey[Seq[(File, String)]](
    "Extra files or directories to include into the artifact")

  lazy val packageAssembleLibraries = settingKey[Boolean](
    "Should the project library dependencies be merged inside the project artifact")

  lazy val packageOutputDir = settingKey[File](
    "Folder to write artifact to")

  lazy val packageArtifactZipFile = settingKey[File](
    "Target file for packaging with packageArtifactZip task")

  lazy val shadePatterns = settingKey[Seq[ShadePattern]](
    "Class renaming patterns in jars")

  lazy val pathExcludeFilter = settingKey[ExcludeFilter.ExcludeFilter](
    "Paths to exclude within merged jars")

  /* Tasks */

  lazy val packageArtifact = taskKey[File](
    "Produce the artifact")

  lazy val packageArtifactDynamic = taskKey[File](
    "Create distribution extracting all classes from projects not marked as static to disk")

  lazy val packageArtifactZip = taskKey[File](
    "Create distribution zip file")

  lazy val findLibraryMapping = inputKey[Seq[(String, Seq[(ModuleKey, Option[String])])]](
    "Find and debug library mappings by library name")


  lazy val dumpDependencyStructure: TaskKey[SbtPackageProjectData] = taskKey("")//.withRank(Invisible)
  lazy val dumpDependencyStructureOffline: TaskKey[SbtPackageProjectData] = taskKey("")//.withRank(Invisible)
  lazy val packageMappings: TaskKey[Mappings] = taskKey("")//.withRank(Invisible)
  lazy val packageMappingsOffline: TaskKey[Mappings] = taskKey("")//.withRank(Invisible)
  lazy val createCompilationTimeStamp: TaskKey[Unit] = taskKey("")//.withRank(Invisible)

}
