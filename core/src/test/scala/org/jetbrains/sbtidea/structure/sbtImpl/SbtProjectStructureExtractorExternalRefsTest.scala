package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.structure._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sbt._
import sbt.internal.BuildDependencies

import java.io.File
import java.net.URI

/**
 * Verifies how [[SbtProjectStructureExtractorBase]] handles '''external''' project references —
 * those introduced by `dependsOn(RootProject(...))`, which belong to a different sbt build and
 * are therefore absent from `projectsData` / `projectMap`.
 *
 * Regression coverage for issue #146: before the fix an external ref reaching `projectMap(ref)`
 * threw a `NoSuchElementException` during IntelliJ import. The cases below exercise the guarded
 * lookups in `topoSortRefs`, `revProjectMap` and `collectChildren`.
 *
 * These scenarios can't be reused from the existing integration-style structure tests: those load
 * a real sbt build where every project shares one build, so no ref is ever "external". Here we
 * drive the trait directly with a synthetic dependency graph and a hand-built [[BuildDependencies]]
 * (via the public `BuildDependencies.apply` factory), which is the only way to introduce a ref
 * that is on the classpath yet missing from `projectMap`.
 */
class SbtProjectStructureExtractorExternalRefsTest extends AnyFunSuite with Matchers {

  private def mkRef(name: String, uri: String = "file:///tmp/build/"): ProjectRef =
    ProjectRef(new URI(uri), name)

  /** Builds a real [[BuildDependencies]] from a plain `ref -> classpath deps` adjacency map. */
  private def mkBuildDependencies(classpathDeps: Map[ProjectRef, Seq[ProjectRef]]): BuildDependencies = {
    val classpath: Map[ProjectRef, Seq[ClasspathDep[ProjectRef]]] =
      classpathDeps.map { case (ref, deps) =>
        ref -> deps.map(dep => ResolvedClasspathDependency(dep, None): ClasspathDep[ProjectRef])
      }
    BuildDependencies(classpath, Map.empty)
  }

  private case class StubProjectData(
    thisProject: ProjectRef,
    cp: sbt.Def.Classpath = Nil,
    definedDeps: Seq[ModuleID] = Nil,
    productDirs: Seq[File] = Nil,
    report: UpdateReport = null
  ) extends CommonSbtProjectData

  private class StubNode(
    val ref: ProjectRef,
    val name: String
  ) extends SbtProjectNode {
    override type T = StubNode
    var parents: Seq[StubNode] = Nil
    var children: Seq[StubNode] = Nil
    var libs: Seq[Library] = Nil
  }

  /**
   * Concrete extractor over the stub types. It implements only the genuinely abstract members
   * (`buildStub`, `updateNode`, `collectLibraries`); all dependency traversal runs against the
   * real [[BuildDependencies]] passed in, so the production `topoSortRefs` / `revProjectMap` /
   * `collectChildren` logic is exercised verbatim — nothing is re-implemented here.
   */
  private class TestExtractor(
    override val rootProject: ProjectRef,
    override val projectsData: Seq[StubProjectData],
    override val buildDependencies: BuildDependencies
  ) extends SbtProjectStructureExtractorBase {
    override type ProjectDataType = StubProjectData
    override type NodeType = StubNode

    override implicit val log: PluginLogger = PluginLogger

    override def buildStub(data: StubProjectData): StubNode =
      new StubNode(data.thisProject, data.thisProject.project)

    override def updateNode(node: StubNode, data: StubProjectData): StubNode = {
      node.children = collectChildren(node, data)
      node.parents = collectParents(node, data)
      node.libs = Nil
      node
    }

    override def collectLibraries(data: StubProjectData): Seq[Library] = Nil

    // expose protected members for testing
    def testTopoSortRefs(root: ProjectRef): Seq[ProjectRef] = topoSortRefs(root)
    def testRevProjectMap: Seq[(ProjectRef, ProjectRef)] = revProjectMap
  }

  test("topoSortRefs skips external ProjectRefs not in projectMap") {
    val internalRef = mkRef("internal-project")
    val externalRef = mkRef("external-project", "file:///tmp/external-build/")

    val projectsData = Seq(StubProjectData(internalRef))
    val extractor = new TestExtractor(internalRef, projectsData,
      mkBuildDependencies(Map(internalRef -> Seq(externalRef)))
    )
    val sorted = extractor.testTopoSortRefs(internalRef)

    sorted should contain(internalRef)
    sorted should not contain externalRef
  }

  test("topoSortRefs returns empty queue when root is external") {
    val externalRef = mkRef("external-project", "file:///tmp/external-build/")

    val extractor = new TestExtractor(externalRef, Seq.empty, mkBuildDependencies(Map.empty))
    val sorted = extractor.testTopoSortRefs(externalRef)

    sorted shouldBe empty
  }

  test("topoSortRefs works normally with all-internal refs") {
    val refA = mkRef("a")
    val refB = mkRef("b")
    val refC = mkRef("c")

    val projectsData = Seq(StubProjectData(refA), StubProjectData(refB), StubProjectData(refC))
    val extractor = new TestExtractor(refA, projectsData,
      mkBuildDependencies(Map(refA -> Seq(refB), refB -> Seq(refC), refC -> Nil))
    )
    val sorted = extractor.testTopoSortRefs(refA)

    sorted should contain allOf(refA, refB, refC)
    sorted.size shouldBe 3
  }

  test("revProjectMap filters out external refs") {
    val internalA = mkRef("a")
    val internalB = mkRef("b")
    val externalRef = mkRef("external", "file:///tmp/external/")

    val projectsData = Seq(StubProjectData(internalA), StubProjectData(internalB))
    val extractor = new TestExtractor(internalA, projectsData,
      mkBuildDependencies(Map(internalA -> Seq(internalB, externalRef), internalB -> Nil))
    )
    val revMap = extractor.testRevProjectMap

    revMap should contain((internalB, internalA))
    revMap.map(_._1) should not contain externalRef
  }

  test("extract succeeds with mixed internal and external dependencies") {
    val internalRef = mkRef("plugin")
    val externalRef = mkRef("library", "file:///tmp/external/")

    val projectsData = Seq(StubProjectData(internalRef))
    val extractor = new TestExtractor(internalRef, projectsData,
      mkBuildDependencies(Map(internalRef -> Seq(externalRef)))
    )
    val result = extractor.extract

    result.size shouldBe 1
    result.head.name shouldBe "plugin"
    result.head.children shouldBe empty
  }
}
