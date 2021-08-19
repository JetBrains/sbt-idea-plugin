package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.artifact.*
import org.jetbrains.sbtidea.packaging.mappings.*
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.{SbtPackageProjectData, SbtPackagingStructureExtractor}
import org.jetbrains.sbtidea.{NullLogger, SbtPluginLogger}
import sbt.*
import sbt.Def.spaceDelimited
import sbt.Keys.*

trait PackagingKeysInit {
  this: PackagingKeys.type =>

  lazy val projectSettings: Seq[Setting[?]] = Seq(
    packageMethod := { // top level project should be packaged as a jar by default
      val workingDir = new File(sys.props("user.dir"))
      val projectRoot = baseDirectory.in(ThisProject).value
      if (workingDir == projectRoot)
        PackagingMethod.Standalone()
      else
        PackagingMethod.MergeIntoParent()
    },
    packageLibraryMappings := { // non top level projects shouldn't have excessive scala-library mappings
      val workingDir = new File(sys.props("user.dir"))
      val projectRoot = baseDirectory.in(ThisProject).value
      if (workingDir == projectRoot)
        Seq.empty
      else
        "org.scala-lang" % "scala3-.*" % ".*"         -> None ::
        "org.scala-lang.modules" % "scala3-.*" % ".*" -> None ::
        "org.scala-lang" % "scala-.*" % ".*"          -> None ::
        "org.scala-lang.modules" % "scala-.*" % ".*"  -> None :: Nil
    },
    packageLibraryBaseDir := file("lib"),
    packageFileMappings := Seq.empty,
    packageAdditionalProjects := Seq.empty,
    packageAssembleLibraries := false,
    shadePatterns := Seq.empty,
    pathExcludeFilter := ExcludeFilter.AllPass,
    packageOutputDir := target.value / "dist",

    packageMappings := {
      streams.value.log.info("started dumping structure")
      packageMappingsImpl(dumpDependencyStructure).value
    },
    packageMappingsOffline := {
      streams.value.log.info("started dumping offline structure")
      packageMappingsImpl(dumpDependencyStructureOffline).value
    },
    findLibraryMapping := {
      val args        = spaceDelimited("<arg>").parsed
      val rootProject = thisProjectRef.value
      val buildDeps   = buildDependencies.value
      val data        = dumpDependencyStructureOffline.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
      val buildStructure = Keys.buildStructure.value
      val structure   = new SbtPackagingStructureExtractor(rootProject, data, buildDeps, buildStructure, NullLogger).extract
      val result = structure.flatMap { node =>
        val mappings     = node.packagingOptions.libraryMappings.toMap
        val matchingLibs = node.libs.filter(lib => args.exists(token => lib.key.toString.contains(token)))
        val filteredLibs = matchingLibs
          .filter(lib => mappings.getOrElse(lib.key, Some("")).isDefined)
          .map   (lib => lib.key -> mappings.getOrElse(lib.key, Some("*")))
        if (filteredLibs.nonEmpty)
          Some(node.name -> filteredLibs)
        else None
      }
      result
    },
    dumpDependencyStructure         := apiAdapter.dumpDependencyStructure.value,
    dumpDependencyStructureOffline  := apiAdapter.dumpDependencyStructureOffline.value,
    packageArtifact := {
      val outputDir = packageOutputDir.value
      val mappings  = packageMappings.value
      val stream    = streams.value
      val myTarget  = target.value
      new DistBuilder(stream, myTarget).produceArtifact(mappings)
      outputDir
    },
    packageArtifactDynamic := {
      val outputDir = packageOutputDir.value
      val mappings = packageMappings.value
      val stream = streams.value
      val myTarget = target.value
      new DynamicDistBuilder(stream, myTarget, outputDir).produceArtifact(mappings)
      outputDir
    },
    packageArtifactZip := doPackageArtifactZip.value,
    doPackageArtifactZip := {
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
    }
  )

  private def packageMappingsImpl(keyFor: TaskKey[SbtPackageProjectData]): Def.Initialize[Task[Mappings]] = Def.task {
    val rootProject = thisProjectRef.value
    val buildDeps = buildDependencies.value
    val data = keyFor.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
    val outputDir = packageOutputDir.value
    val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
    val buildStructure = Keys.buildStructure.value
    val structure = new SbtPackagingStructureExtractor(rootProject, data, buildDeps, buildStructure, logger).extract
    val res = new LinearMappingsBuilder(outputDir, logger).buildMappings(structure)
    logger.throwFatalErrors()
    res
  }

}
