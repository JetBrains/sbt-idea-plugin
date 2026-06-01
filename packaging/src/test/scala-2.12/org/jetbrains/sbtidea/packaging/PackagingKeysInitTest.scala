package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.structure.sbtImpl.SbtPackageProjectData
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sbt._

import java.io.File
import java.net.URI

/**
 * Unit tests for PackagingKeysInit.buildExternalProjectData.
 *
 * The interesting behavior is that the helper is agnostic to which SBT task supplied
 * `allProducts` — callers are free to pass values from `Compile / products` (for real
 * packaging, where class files must exist on disk) or from `Compile / productDirectories`
 * (for the offline path used during IntelliJ sync via createIDEAArtifactXml, where
 * forcing a compile of every project would break sync on a dependent compile error).
 *
 * Locking that contract here means the call sites in PackagingKeysInit — which wire
 * `productDirectories` for the offline path — cannot regress without this test failing.
 */
class PackagingKeysInitTest extends AnyWordSpec with Matchers {

  private def mkRef(name: String): ProjectRef =
    ProjectRef(new URI("file:///tmp/build/"), name)

  "buildExternalProjectData" should {

    "pass productDirectories through unchanged for external projects (offline path)" in {
      val pluginRef   = mkRef("ij-plugin")
      val externalRef = mkRef("external-lib")

      val externalProductDirs = Seq(new File("/tmp/external-lib/target/classes"))

      val result = PackagingKeysInit.buildExternalProjectData(
        pluginRefs     = Set(pluginRef),
        allRefs        = Seq(pluginRef, externalRef),
        allNames       = Seq("ij-plugin", "external-lib"),
        allProducts    = Seq(Seq(new File("/tmp/ij-plugin/target/classes")), externalProductDirs),
        allClasspaths  = Seq(Nil, Nil),
        allDefinedDeps = Seq(Nil, Nil),
        allReports     = Seq(null, null),
      )

      // pluginRef already covered by pluginData — only external project should appear
      result.map(_.thisProject) shouldBe Seq(externalRef)
      // and whatever the caller supplied for allProducts(i) must reach productDirs verbatim
      result.head.productDirs shouldBe externalProductDirs
    }

    "pass products through unchanged for external projects (online path)" in {
      // Same contract from the online direction: real compiled outputs reach productDirs
      // verbatim. If anyone later inlines the productsKey back to (Compile / products),
      // both this and the offline-path test still pass — but the offline-path comment
      // and call-site wiring stay the line of defense.
      val pluginRef   = mkRef("ij-plugin")
      val externalRef = mkRef("external-lib")

      val externalCompiledOutputs = Seq(
        new File("/tmp/external-lib/target/classes"),
        new File("/tmp/external-lib/target/extra-classes"),
      )

      val result = PackagingKeysInit.buildExternalProjectData(
        pluginRefs     = Set(pluginRef),
        allRefs        = Seq(pluginRef, externalRef),
        allNames       = Seq("ij-plugin", "external-lib"),
        allProducts    = Seq(Nil, externalCompiledOutputs),
        allClasspaths  = Seq(Nil, Nil),
        allDefinedDeps = Seq(Nil, Nil),
        allReports     = Seq(null, null),
      )

      result.head.productDirs shouldBe externalCompiledOutputs
    }

    "return empty when every project is already covered by pluginData" in {
      val a = mkRef("a")
      val b = mkRef("b")

      val result = PackagingKeysInit.buildExternalProjectData(
        pluginRefs     = Set(a, b),
        allRefs        = Seq(a, b),
        allNames       = Seq("a", "b"),
        allProducts    = Seq(Nil, Nil),
        allClasspaths  = Seq(Nil, Nil),
        allDefinedDeps = Seq(Nil, Nil),
        allReports     = Seq(null, null),
      )

      result shouldBe empty
    }

    "preserve index correlation across multiple external projects" in {
      val pluginRef = mkRef("ij-plugin")
      val extA      = mkRef("ext-a")
      val extB      = mkRef("ext-b")

      val productsA = Seq(new File("/tmp/ext-a/target/classes"))
      val productsB = Seq(new File("/tmp/ext-b/target/classes"))
      val namesIn   = Seq("ij-plugin", "ext-a", "ext-b")

      val result: Seq[SbtPackageProjectData] = PackagingKeysInit.buildExternalProjectData(
        pluginRefs     = Set(pluginRef),
        allRefs        = Seq(pluginRef, extA, extB),
        allNames       = namesIn,
        allProducts    = Seq(Nil, productsA, productsB),
        allClasspaths  = Seq(Nil, Nil, Nil),
        allDefinedDeps = Seq(Nil, Nil, Nil),
        allReports     = Seq(null, null, null),
      )

      // Each external project's data must come from the same index across allRefs/allNames/allProducts/etc.
      val byRef = result.map(d => d.thisProject -> d).toMap
      byRef(extA).thisProjectName shouldBe "ext-a"
      byRef(extA).productDirs     shouldBe productsA
      byRef(extB).thisProjectName shouldBe "ext-b"
      byRef(extB).productDirs     shouldBe productsB
    }

    "default external projects to MergeIntoParent() with no opinionated libMappings" in {
      val pluginRef   = mkRef("ij-plugin")
      val externalRef = mkRef("external-lib")

      val result = PackagingKeysInit.buildExternalProjectData(
        pluginRefs     = Set(pluginRef),
        allRefs        = Seq(pluginRef, externalRef),
        allNames       = Seq("ij-plugin", "external-lib"),
        allProducts    = Seq(Nil, Nil),
        allClasspaths  = Seq(Nil, Nil),
        allDefinedDeps = Seq(Nil, Nil),
        allReports     = Seq(null, null),
      )

      val ext = result.head
      ext.packageMethod shouldBe PackagingMethod.MergeIntoParent()
      ext.libMapping    shouldBe empty
      ext.shadePatterns shouldBe empty
    }
  }
}
