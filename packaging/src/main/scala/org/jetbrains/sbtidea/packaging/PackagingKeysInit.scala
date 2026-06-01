package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.artifact.*
import org.jetbrains.sbtidea.packaging.mappings.*
import org.jetbrains.sbtidea.packaging.structure.sbtImpl.{SbtPackageProjectData, SbtPackagingStructureExtractor}
import org.jetbrains.sbtidea.{NullLogger, SbtPluginLogger}
import sbt.*
import sbt.Def.spaceDelimited
import sbt.Keys.*

object PackagingKeysInit {
  // Pure assembly of SbtPackageProjectData for external projects (those not already
  // covered by pluginData). Extracted from packageMappingsImpl so the data plumbing can
  // be unit-tested without an SBT environment — see ExternalProjectPackagingTest.
  def buildExternalProjectData(
    pluginRefs: Set[ProjectRef],
    allRefs: Seq[ProjectRef],
    allNames: Seq[String],
    allProducts: Seq[Seq[File]],
    allClasspaths: Seq[Def.Classpath],
    allDefinedDeps: Seq[Seq[ModuleID]],
    allReports: Seq[UpdateReport],
  ): Seq[SbtPackageProjectData] =
    allRefs.indices
      .filterNot(i => pluginRefs.contains(allRefs(i)))
      .map { i =>
        SbtPackageProjectData(
          thisProject = allRefs(i),
          thisProjectName = allNames(i),
          cp = allClasspaths(i),
          definedDeps = allDefinedDeps(i),
          additionalProjects = Seq.empty,
          assembleLibraries = false,
          productDirs = allProducts(i),
          report = allReports(i),
          libMapping = Seq.empty,
          libraryBaseDir = file("lib"),
          additionalMappings = Seq.empty,
          packageMethod = PackagingMethod.MergeIntoParent(),
          shadePatterns = Seq.empty,
          excludeFilter = ExcludeFilter.AllPass
        )
      }
}

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
      packageMappingsImpl(dumpDependencyStructure, Compile / products).value
    },
    packageMappingsOffline := {
      streams.value.log.info("started dumping offline structure")
      // Offline must use productDirectories (path-only). products would force a compile
      // of every project during IntelliJ sync via createIDEAArtifactXml → packageMappingsOffline,
      // and a compile error in any external RootProject would then break sync entirely.
      packageMappingsImpl(dumpDependencyStructureOffline, Compile / productDirectories).value
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

  private def packageMappingsImpl(
    keyFor: TaskKey[SbtPackageProjectData],
    productsKey: TaskKey[Seq[File]],
  ): Def.Initialize[Task[Mappings]] = Def.task {
    val rootProject = thisProjectRef.value
    val buildDeps = buildDependencies.value
    val pluginData = keyFor.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
    val externalData = externalProjectData(keyFor, productsKey).value

    val data = pluginData ++ externalData
    val outputDir = packageOutputDir.value
    val logger: SbtPluginLogger = new SbtPluginLogger(streams.value)
    val buildStructure = Keys.buildStructure.value
    val structure = new SbtPackagingStructureExtractor(rootProject, data, buildDeps, buildStructure, logger).extract
    val res = new LinearMappingsBuilder(outputDir, logger, Some(rootProject)).buildMappings(structure)
    logger.throwFatalErrors()
    res
  }

  /**
   * Sub-task that collects packaging data for external projects loaded via
   * `RootProject` / `ProjectRef` — those not already covered by `keyFor`.
   *
   * External projects have their own builds and don't load sbt-idea-plugin, so
   * `dumpDependencyStructure` is undefined for them and they are absent from `keyFor`'s data.
   * Without this, their class files and library dependencies would never reach the plugin
   * artifact, forcing consumers into `packageFileMappings` with explicit JARs (which breaks
   * source-level debugging) and manual `libraryDependencies` pulls.
   *
   * We read project refs, names, class output, managed classpaths, library dependencies, and
   * full dependency reports (`updateFull`) from ALL projects using standard SBT keys (available
   * everywhere), then [[PackagingKeysInit.buildExternalProjectData]] turns the projects not in
   * `keyFor` into [[SbtPackageProjectData]] defaulting to `MergeIntoParent()` — so their classes
   * and libraries merge into the parent plugin artifact, just like `dependsOn` for normal
   * (non-IJ) SBT projects. The full report (not null) lets `IvyLibraryExtractor` resolve
   * transitive dependencies without special null-handling.
   *
   * `productsKey` is supplied by the caller to mirror the online/offline split that already
   * exists for `dumpDependencyStructure(Offline)`: `packageMappings` passes `Compile / products`
   * (compile-triggering, needed to actually package class files); `packageMappingsOffline` passes
   * `Compile / productDirectories` (path-only). The offline variant runs during IntelliJ sync via
   * `createIDEAArtifactXml`, so using `products` there would force a compile of every project on
   * sync, and a compile error in any external `RootProject` would break sync entirely.
   *
   * For IJ plugins with no external `RootProject` dependencies this returns an empty sequence
   * (no-op) — every project is already covered by `keyFor`.
   */
  private def externalProjectData(
    keyFor: TaskKey[SbtPackageProjectData],
    productsKey: TaskKey[Seq[File]],
  ): Def.Initialize[Task[Seq[SbtPackageProjectData]]] = Def.task {
    val pluginRefs = keyFor.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null).map(_.thisProject).toSet
    val allRefs = thisProjectRef.all(ScopeFilter(inAnyProject)).value
    val allNames = name.all(ScopeFilter(inAnyProject)).value
    val allProducts = productsKey.all(ScopeFilter(inAnyProject)).value
    val allClasspaths = (Runtime / managedClasspath).all(ScopeFilter(inAnyProject)).value
    val allDefinedDeps = (Compile / libraryDependencies).all(ScopeFilter(inAnyProject)).value
    val allReports = updateFull.all(ScopeFilter(inAnyProject)).value
    PackagingKeysInit.buildExternalProjectData(
      pluginRefs, allRefs, allNames, allProducts, allClasspaths, allDefinedDeps, allReports
    )
  }

}
