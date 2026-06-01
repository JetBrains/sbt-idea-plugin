package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.CapturingLogger
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

    "exclude non-scala libraries from external projects when root project has exclusions without invalid mapping errors" in {
      // User POV:
      // A plugin project can depend on a normal sbt project via `dependsOn(RootProject(...))`.
      // The external project does not enable sbt-idea-plugin, so it has no own `packageLibraryMappings`;
      // the root plugin project's mappings are the user-visible way to exclude or remap its libraries.
      //
      // Therefore a root-level exclusion for an external-only library is valid when that library is present
      // in the external project, even if it is absent from the root plugin module itself.
      // It must exclude the external library and must not be reported as "No library dependencies match mapping".
      //
      // Related:
      // - https://github.com/JetBrains/sbt-idea-plugin/issues/146
      // - https://github.com/JetBrains/sbt-idea-plugin/pull/147
      // - https://github.com/JetBrains/sbt-idea-plugin/issues/68
      val appLibJar = new File("/tmp/cats-core.jar")
      val appLibKey = mkKey("org.typelevel", "cats-core_3", "2.13.0")
      val appLib = new TestLibrary(appLibKey, Seq(appLibJar))

      val pluginNode = node(
        "main-plugin",
        SPackagingMethod.Standalone("lib/main-plugin.jar", static = false),
        libraryMappings = Seq(appLibKey -> None)
      )
      val externalNode = node(
        "external-lib",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        libs = Seq(appLib),
        libraryMappings = Seq.empty
      )

      val (messages, mappings) = CapturingLogger.captureLogAndValue() {
        new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(externalNode, pluginNode))
      }

      mappings.exists(_.from == appLibJar) shouldBe false
      messages.exists(_.contains("No library dependencies match mapping")) shouldBe false
    }

    "use root plugin library mappings when another standalone node appears before root" in {
      // User POV:
      // `packageLibraryMappings` configured on the root plugin project are documented as global
      // defaults for all packaging nodes, including external RootProject dependencies. That contract
      // should not depend on the internal order in which the extracted packaging graph is traversed.
      //
      // A build may contain another Standalone-packaged node before the actual root plugin node
      // (for example, in multi-module/plugin-model setups).
      // Such a node is a valid packaging target, but it is not the root plugin project whose mappings the user configured.
      // The external project's libraries should still be filtered by the real root plugin mappings.
      //
      // Related:
      // - https://github.com/JetBrains/sbt-idea-plugin/issues/146
      // - https://github.com/JetBrains/sbt-idea-plugin/pull/147
      // - https://youtrack.jetbrains.com/issue/SCL-21681
      val scalaLibJar = new File("/tmp/scala-library-3.7.4.jar")
      val scalaLibKey = mkKey("org.scala-lang", "scala3-library_3", "3.7.4")
      val scalaLib = new TestLibrary(scalaLibKey, Seq(scalaLibJar))

      val pluginNode = node(
        "main-plugin",
        SPackagingMethod.Standalone("lib/main-plugin.jar", static = false),
        libraryMappings = Seq(scalaLibKey -> None)
      )
      val standaloneSubmodule = node(
        "standalone-submodule",
        SPackagingMethod.Standalone("lib/standalone-submodule.jar", static = false),
        parents = Seq(pluginNode),
        libraryMappings = Seq.empty
      )
      val externalNode = node(
        "external-lib",
        SPackagingMethod.MergeIntoParent(),
        parents = Seq(pluginNode),
        libs = Seq(scalaLib),
        libraryMappings = Seq.empty
      )

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(
        Seq(externalNode, standaloneSubmodule, pluginNode)
      )

      mappings.exists(_.from == scalaLibJar) shouldBe false
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
