package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.structure.{PackagedProjectNode, PackagingMethod => SPackagingMethod, ProjectPackagingOptions}
import org.jetbrains.sbtidea.structure.{Library, ModuleKey}
import org.jetbrains.sbtidea.structure.sbtImpl.ModuleKeyImpl
import sbt.ModuleID

import java.io.File

/**
 * Shared in-memory [[PackagedProjectNode]] fixtures for [[org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder]]
 * tests. Both `LinearMappingsBuilderMergeWarningsTest` and `ExternalProjectPackagingTest` build the
 * same kind of synthetic node graph (a node with a packaging method, parents, class roots, libs and
 * library mappings) and run it through `LinearMappingsBuilder.buildMappings`. Keeping one
 * parameterized `node(...)` here avoids the divergent copies that previously lived in each test.
 *
 * Every constructor parameter except `name`/`method` defaults to empty, so callers only supply the
 * fields a given scenario cares about.
 */
trait PackagingTestNodes {

  protected def node(
    name: String,
    method: SPackagingMethod,
    parents: Seq[PackagedProjectNode] = Seq.empty,
    classRoots: Seq[File] = Seq.empty,
    libs: Seq[Library] = Seq.empty,
    libraryMappings: Seq[(ModuleKey, Option[String])] = Seq.empty
  ): TestNode =
    new TestNode(
      name = name,
      parents0 = parents,
      libs0 = libs,
      packagingOptions = packagingOptions(method, classRoots, libraryMappings)
    )

  protected def packagingOptions(
    method: SPackagingMethod,
    classRoots0: Seq[File],
    libraryMappings0: Seq[(ModuleKey, Option[String])]
  ): ProjectPackagingOptions =
    new ProjectPackagingOptions {
      override def packageMethod: SPackagingMethod = method
      override def libraryMappings: Seq[(ModuleKey, Option[String])] = libraryMappings0
      override def libraryBaseDir: File = new File("lib")
      override def fileMappings: Seq[(File, String)] = Seq.empty
      override def shadePatterns: Seq[ShadePattern] = Seq.empty
      override def excludeFilter: ExcludeFilter = ExcludeFilter.AllPass
      override def additionalProjects: Seq[PackagedProjectNode] = Seq.empty
      override def classRoots: Seq[File] = classRoots0
      override def assembleLibraries: Boolean = false
    }

  protected final class TestNode(
    override val name: String,
    parents0: Seq[PackagedProjectNode],
    libs0: Seq[Library],
    override val packagingOptions: ProjectPackagingOptions
  ) extends PackagedProjectNode {
    override val rootProjectName: Option[String] = None
    override val parents: Seq[PackagedProjectNode] = parents0
    override val children: Seq[PackagedProjectNode] = Seq.empty
    override val libs: Seq[Library] = libs0

    override def toString: String = s"{$name}"
  }

  protected def mkKey(org: String, name: String, rev: String): ModuleKey =
    ModuleKeyImpl(ModuleID(org, name, rev), Map.empty)

  protected class TestLibrary(
    override val key: ModuleKey,
    override val jarFiles: Seq[File]
  ) extends Library
}
