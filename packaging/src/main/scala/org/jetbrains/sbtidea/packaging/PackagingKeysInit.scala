package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.SbtPluginLogger
import org.jetbrains.sbtidea.packaging.artifact._
import org.jetbrains.sbtidea.packaging.mappings._
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.{SbtPackageProjectData, SbtPackagingStructureExtractor}
import sbt.Keys._
import sbt._
import sbt.jetbrains.ideaPlugin.apiAdapter._

trait PackagingKeysInit {
  this: PackagingKeys.type =>

  private var compilationTimeStamp = -1L

  lazy val buildSettings: Seq[Setting[_]] = Seq(
    packageMethod.in(LocalRootProject) := PackagingMethod.Standalone(),
    packageLibraryMappings.in(LocalRootProject) := Seq.empty
  )

  lazy val projectSettings: Seq[Setting[_]] = Seq(
    packageMethod := PackagingMethod.MergeIntoParent(),
    packageLibraryMappings := "org.scala-lang" % "scala-.*" % ".*" -> None ::
                              "org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil,
    packageFileMappings := Seq.empty,
    packageAdditionalProjects := Seq.empty,
    packageAssembleLibraries := false,
    shadePatterns := Seq.empty,
    pathExcludeFilter := ExcludeFilter.AllPass,
    packageOutputDir := target.value / "dist",

    createCompilationTimeStamp := Def.task { compilationTimeStamp = System.currentTimeMillis() }.value,
    packageMappings := Def.taskDyn {
      streams.value.log.info("started dumping structure")
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructure.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
      val outputDir = packageOutputDir.value
      val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
      Def.task {
        val structure = new SbtPackagingStructureExtractor(rootProject, data, buildDeps, logger).extract
        new LinearMappingsBuilder(outputDir, logger).buildMappings(structure)
      }
    }.value,
    packageMappingsOffline := Def.taskDyn {
      streams.value.log.info("started dumping offline structure")
      val rootProject = thisProjectRef.value
      val buildDeps = buildDependencies.value
      val data = dumpDependencyStructureOffline.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
      val outputDir = packageOutputDir.value
      val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
      Def.task {
        val structure = new SbtPackagingStructureExtractor(rootProject, data, buildDeps, logger).extract
        new LinearMappingsBuilder(outputDir, logger).buildMappings(structure)
      }
    }.value,
    dumpDependencyStructure := Def.task {
      SbtPackageProjectData(
        thisProjectRef.value,
        managedClasspath.in(Compile).value,
        libraryDependencies.in(Compile).value,
        packageAdditionalProjects.value,
        packageAssembleLibraries.value,
        products.in(Compile).value,
        update.value,
        packageLibraryMappings.value,
        packageFileMappings.value,
        packageMethod.value,
        shadePatterns.value,
        pathExcludeFilter.value
      )
    }.value,
    dumpDependencyStructureOffline := Def.task {
      SbtPackageProjectData(
        thisProjectRef.value,
        managedClasspath.in(Compile).value,
        libraryDependencies.in(Compile).value,
        packageAdditionalProjects.value,
        packageAssembleLibraries.value,
        productDirectories.in(Compile).value,
        update.value,
        packageLibraryMappings.value,
        packageFileMappings.value,
        packageMethod.value,
        shadePatterns.value,
        pathExcludeFilter.value
      )
    }.value,
    packageArtifact := Def.taskDyn {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      val stream    = streams.value
      val myTarget  = target.value
      Def.task { new DistBuilder(stream, myTarget).produceArtifact(mappings); outputDir }
    }.value,
    packageArtifactDynamic := Def.sequential(createCompilationTimeStamp, Def.task {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      val stream    = streams.value
      val myTarget  = target.value
      val hints = extractAffectedFiles(compilationTimeStamp, compile.all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value)
      new DynamicDistBuilder(stream, myTarget, outputDir, hints).produceArtifact(mappings)
      outputDir
    }).value,
    packageArtifactZip := Def.task {
      implicit val stream: TaskStreams = streams.value
      val outputDir = packageArtifact.value.getParentFile
      packageArtifactZipFile.?.value match {
        case None =>
          stream.log.error("please define packageArtifactZipFile key to use this task")
          file("")
        case Some(file) =>
          IO.delete(file)
          new ZipDistBuilder(file).produceArtifact(outputDir)
          file
      }
    }.value
  )
}
