package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackageProjectData
import org.jetbrains.sbtidea.structure.sbtImpl.CommonSbtProjectData
import sbt.KeyRanks.Invisible
import sbt._

object PackagingKeys extends PackagingDefs with PackagingKeysInit {

  /* Settings */

  lazy val packageMethod = SettingKey[PackagingMethod](
    "package-method",
    "What kind of artifact to produce from given project"
  )

  lazy val packageAdditionalProjects = SettingKey[Seq[Project]](
    "package-additional-projects",
    "Projects to package alongside current, without adding classpath dependencies"
  )

  lazy val packageLibraryMappings = SettingKey[Seq[(ModuleID, Option[String])]](
    "package-library-mappings",
    "Overrides for library mappings in artifact"
  )

  lazy val packageFileMappings = SettingKey[Seq[(File, String)]](
    "package-file-mappings",
    "Extra files or directories to include into the artifact"
  )

  lazy val packageAssembleLibraries = SettingKey[Boolean](
    "package-assemble-libraries",
    "Should the project library dependencies be merged inside the project artifact"
  )

  lazy val packageOutputDir = SettingKey[File](
    "package-output-dir",
    "Folder to write plugin artifact to"
  )

  lazy val packageArtifactZipFile: SettingKey[File] = settingKey[File](
    "Target file for packaging with packageArtifactZip task"
  )

  lazy val shadePatterns = SettingKey[Seq[ShadePattern]](
    "shade-patterns",
    "Class renaming patterns in jars"
  )

  lazy val pathExcludeFilter = SettingKey[ExcludeFilter.ExcludeFilter](
    "path-exclude-filter",
    "paths to exclude within merged jars"
  )

  /* Tasks */

  lazy val packageArtifact = TaskKey[File](
    "package-artifact",
    "Create plugin distribution"
  )

  lazy val packageArtifactDynamic = TaskKey[File](
    "package-plugin-dynamic",
    "Create plugin distribution extracting all classes from projects not marked as static to disk"
  )

  lazy val packageArtifactZip = TaskKey[File](
    "package-plugin-zip",
    "Create plugin distribution zip file"
  )


  lazy val dumpDependencyStructure: TaskKey[SbtPackageProjectData] = taskKey("")//.withRank(Invisible)
  lazy val dumpDependencyStructureOffline: TaskKey[SbtPackageProjectData] = taskKey("")//.withRank(Invisible)
  lazy val packageMappings: TaskKey[Mappings] = taskKey("")//.withRank(Invisible)
  lazy val packageMappingsOffline: TaskKey[Mappings] = taskKey("")//.withRank(Invisible)
  lazy val createCompilationTimeStamp: TaskKey[Unit] = taskKey("")//.withRank(Invisible)

}
