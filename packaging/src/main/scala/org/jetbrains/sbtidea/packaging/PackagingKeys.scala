package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackageProjectData
import org.jetbrains.sbtidea.structure.ModuleKey
import sbt.*

object PackagingKeys extends PackagingKeysInit with PackagingDefs {

  //=========================================================
  // Settings
  //=========================================================

  lazy val packageMethod: SettingKey[PackagingKeys.PackagingMethod] = settingKey[PackagingMethod](
    "What kind of artifact to produce from given project")

  lazy val packageAdditionalProjects: SettingKey[Seq[Project]] = settingKey[Seq[Project]](
    "Projects to package alongside current, without adding classpath dependencies")
  
  lazy val packageLibraryMappings: SettingKey[Seq[(sbt.ModuleID, Option[String])]] = settingKey[Seq[(ModuleID, Option[String])]](
    "Overrides for library mappings in artifact")

  lazy val packageLibraryBaseDir: SettingKey[sbt.File] = settingKey[File](
    "Directory to place library dependencies into. *Relative* to the artifact output dir")

  lazy val packageAssembleLibraries: SettingKey[Boolean] = settingKey[Boolean](
    "Should the project library dependencies be merged inside the project artifact")

  lazy val packageOutputDir: SettingKey[sbt.File] = settingKey[File](
    "Folder to write artifact to")

  lazy val packageArtifactZipFile: SettingKey[sbt.File] = settingKey[File](
    "Target file for packaging with packageArtifactZip task")

  lazy val shadePatterns: SettingKey[Seq[PackagingKeys.ShadePattern]] = settingKey[Seq[ShadePattern]](
    "Class renaming patterns in jars")

  lazy val pathExcludeFilter: SettingKey[ExcludeFilter] = settingKey[ExcludeFilter](
    "Paths to exclude within merged jars")

  //=========================================================
  // Tasks
  //=========================================================

  lazy val packageFileMappings = taskKey[Seq[(File, String)]](
    "Extra files or directories to include into the artifact")

  lazy val packageArtifact = taskKey[File](
    "Produce the artifact")

  lazy val packageArtifactDynamic = taskKey[File](
    "Create distribution extracting all classes from projects not marked as static to disk")

  lazy val packageArtifactZip = taskKey[File](
    "Create distribution zip file")

  lazy val findLibraryMapping = inputKey[Seq[(String, Seq[(ModuleKey, Option[String])])]](
    "Find and debug library mappings by library name")


  lazy val dumpDependencyStructure: TaskKey[SbtPackageProjectData] =
    taskKey[SbtPackageProjectData]("").withRank(sbt.KeyRanks.Invisible)

  lazy val dumpDependencyStructureOffline: TaskKey[SbtPackageProjectData] =
    taskKey[SbtPackageProjectData]("").withRank(sbt.KeyRanks.Invisible)

  lazy val packageMappings: TaskKey[Mappings] =
    taskKey[Mappings]("").withRank(sbt.KeyRanks.Invisible)

  lazy val packageMappingsOffline: TaskKey[Mappings] =
    taskKey[Mappings]("").withRank(sbt.KeyRanks.Invisible)

  lazy val doPackageArtifactZip: TaskKey[sbt.File] =
    taskKey[File]("").withRank(sbt.KeyRanks.Invisible)
}
