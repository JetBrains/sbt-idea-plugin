package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.{PackagingMethod => SPackagingMethod}
import org.jetbrains.sbtidea.structure.ModuleKey
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

/**
 * Tests for external project packaging support.
 *
 * When an IntelliJ plugin project uses dependsOn(RootProject(...)) to depend on external
 * SBT projects, those projects don't have sbt-idea-plugin enabled. The packaging system
 * collects their data using standard SBT keys and includes them with MergeIntoParent(),
 * so their class files and libraries are merged into the parent plugin artifact.
 *
 * These tests verify that the packaging system correctly handles external projects
 * alongside normal plugin projects.
 *
 * Node fixtures come from [[PackagingTestNodes]], shared with `LinearMappingsBuilderMergeWarningsTest`.
 */
class ExternalProjectPackagingTest extends AnyWordSpec with Matchers with PackagingTestNodes {

  private val outputDir = new File("target/test-packaging")

  "External project packaging" should {

    "merge external project class files into parent plugin artifact" in {
      val externalClassDir = new File("/tmp/external-project/target/classes")
      val pluginNode = node("main-plugin", SPackagingMethod.Standalone("lib/main-plugin.jar", static = false))
      val externalNode = node(
        "external-lib",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        classRoots = Seq(externalClassDir)
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(externalNode, pluginNode))

      // External project's class directory should be mapped to the parent's JAR
      val externalMappings = mappings.filter(_.from == externalClassDir)
      externalMappings should not be empty
      externalMappings.head.to.toString should include("main-plugin.jar")
    }

    "include external project libraries in packaging output" in {
      val libraryJar = new File("/tmp/external-project/lib/some-library.jar")
      val libraryKey = mkKey("org.example", "some-library", "1.0")
      val library = new TestLibrary(libraryKey, Seq(libraryJar))

      val pluginNode = node("main-plugin", SPackagingMethod.Standalone("lib/main-plugin.jar", static = false))
      val externalNode = node(
        "external-lib",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        libs = Seq(library)
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(externalNode, pluginNode))

      // External project's library JAR should appear in the mappings
      val libraryMappings = mappings.filter(_.from == libraryJar)
      libraryMappings should not be empty
    }

    "work normally when there are no external projects" in {
      val pluginClassDir = new File("/tmp/plugin/target/classes")
      val subprojectClassDir = new File("/tmp/subproject/target/classes")

      val pluginNode = node(
        "main-plugin",
        SPackagingMethod.Standalone("lib/main-plugin.jar", static = false),
        classRoots = Seq(pluginClassDir)
      )
      val subprojectNode = node(
        "sub-module",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        classRoots = Seq(subprojectClassDir)
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(subprojectNode, pluginNode))

      // Both projects' classes should be mapped
      mappings.exists(_.from == pluginClassDir) shouldBe true
      mappings.exists(_.from == subprojectClassDir) shouldBe true
    }

    "include all libraries from external projects when root has no exclusions" in {
      val scalaLibJar = new File("/tmp/scala-library-3.7.4.jar")
      val scalaLibKey = mkKey("org.scala-lang", "scala3-library_3", "3.7.4")
      val scalaLib = new TestLibrary(scalaLibKey, Seq(scalaLibJar))

      val appLibJar = new File("/tmp/cats-core.jar")
      val appLibKey = mkKey("org.typelevel", "cats-core_3", "2.13.0")
      val appLib = new TestLibrary(appLibKey, Seq(appLibJar))

      // Both root and external project have empty libraryMappings — all libs included.
      val pluginNode = node("main-plugin", SPackagingMethod.Standalone("lib/main-plugin.jar", static = false))
      val externalNode = node(
        "external-lib",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        libs = Seq(scalaLib, appLib),
        libraryMappings = Seq.empty
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(externalNode, pluginNode))

      mappings.exists(_.from == scalaLibJar) shouldBe true
      mappings.exists(_.from == appLibJar) shouldBe true
    }

    "exclude libraries from external projects when root project has exclusions" in {
      val scalaLibJar = new File("/tmp/scala-library-3.7.4.jar")
      val scalaLibKey = mkKey("org.scala-lang", "scala3-library_3", "3.7.4")
      val scalaLib = new TestLibrary(scalaLibKey, Seq(scalaLibJar))

      val appLibJar = new File("/tmp/cats-core.jar")
      val appLibKey = mkKey("org.typelevel", "cats-core_3", "2.13.0")
      val appLib = new TestLibrary(appLibKey, Seq(appLibJar))

      // Root project excludes scala libraries via libraryMappings.
      // External project has empty libMapping, so root's exclusions apply as defaults.
      val scalaExclusions: Seq[(ModuleKey, Option[String])] = Seq(scalaLibKey -> None)
      val pluginNode = node(
        "main-plugin",
        SPackagingMethod.Standalone("lib/main-plugin.jar", static = false),
        libraryMappings = scalaExclusions
      )
      val externalNode = node(
        "external-lib",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        libs = Seq(scalaLib, appLib),
        libraryMappings = Seq.empty
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(externalNode, pluginNode))

      // scala-library excluded by root's mappings, cats-core included
      mappings.exists(_.from == scalaLibJar) shouldBe false
      mappings.exists(_.from == appLibJar) shouldBe true
    }

    "exclude scala when both root and node have the same exclusions" in {
      val scalaLibJar = new File("/tmp/scala-library-3.7.4.jar")
      val scalaLibKey = mkKey("org.scala-lang", "scala3-library_3", "3.7.4")
      val scalaLib = new TestLibrary(scalaLibKey, Seq(scalaLibJar))

      val appLibJar = new File("/tmp/cats-core.jar")
      val appLibKey = mkKey("org.typelevel", "cats-core_3", "2.13.0")
      val appLib = new TestLibrary(appLibKey, Seq(appLibJar))

      // Both root and subproject exclude scala — should still work (no double-processing issues)
      val scalaExclusions: Seq[(ModuleKey, Option[String])] = Seq(scalaLibKey -> None)
      val pluginNode = node(
        "main-plugin",
        SPackagingMethod.Standalone("lib/main-plugin.jar", static = false),
        libraryMappings = scalaExclusions
      )
      val subprojectNode = node(
        "sub-module",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        libs = Seq(scalaLib, appLib),
        libraryMappings = scalaExclusions
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(subprojectNode, pluginNode))

      mappings.exists(_.from == scalaLibJar) shouldBe false
      mappings.exists(_.from == appLibJar) shouldBe true
    }

    "node-specific libraryMappings override root exclusions" in {
      val scalaReflectJar = new File("/tmp/scala-reflect-2.13.15.jar")
      val scalaReflectKey = mkKey("org.scala-lang", "scala-reflect", "2.13.15")
      val scalaReflectLib = new TestLibrary(scalaReflectKey, Seq(scalaReflectJar))

      // Root excludes all scala-lang, but subproject explicitly includes scala-reflect
      val rootExclusions: Seq[(ModuleKey, Option[String])] = Seq(
        mkKey("org.scala-lang", "scala-.*", ".*") -> None
      )
      val nodeOverride: Seq[(ModuleKey, Option[String])] = Seq(
        scalaReflectKey -> Some("lib/scala-reflect.jar")
      )

      val pluginNode = node(
        "main-plugin",
        SPackagingMethod.Standalone("lib/main-plugin.jar", static = false),
        libraryMappings = rootExclusions
      )
      val subprojectNode = node(
        "sub-module",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        libs = Seq(scalaReflectLib),
        libraryMappings = nodeOverride
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(subprojectNode, pluginNode))

      // Node's explicit include wins over root's exclusion
      mappings.exists(_.from == scalaReflectJar) shouldBe true
    }
  }
}
