package org.jetbrains.sbtidea.packaging

import io.circe.parser.decode
import io.circe.syntax.*
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.{PackagingMethod as SPackagingMethod}
import org.jetbrains.sbtidea.packaging.testUtils.CirceEncodersDecoders.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

class PluginModulePackagingTest extends AnyWordSpec with Matchers with PackagingTestNodes {
  private val outputDir = new File("target/test-plugin-modules")

  "Plugin module packaging" should {
    "keep ordinary modules in lib/modules by default" in {
      val classes = new File("module/target/classes")
      val module = node("module", SPackagingMethod.PluginModule("example.module", static = false), classRoots = Seq(classes))

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(module))

      mappings.map(_.to) should contain only new File(outputDir, "lib/modules/example.module.jar")
      mappings.head.metaData.static shouldBe false
    }

    "put embedded modules in lib while preserving the dynamic packaging choice" in {
      for (isStatic <- Seq(false, true)) {
        val classes = new File("module/target/classes")
        val module = node("module", SPackagingMethod.PluginModule("example.module", static = isStatic, embedded = true),
          classRoots = Seq(classes))

        val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(module))

        mappings.map(_.to) should contain only new File(outputDir, "lib/example.module.jar")
        mappings.head.metaData.static shouldBe isStatic
      }
    }

    "combine projects with the same embedded module name into one jar" in {
      val mainClasses = new File("module/target/classes")
      val helperClasses = new File("helper/target/classes")
      val method = SPackagingMethod.PluginModule("example.module", static = false, embedded = true)
      val module = node("module", method, classRoots = Seq(mainClasses))
      val helper = node("helper", method, parents = Seq(module), classRoots = Seq(helperClasses))

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(module, helper))

      mappings.map(_.from) should contain theSameElementsAs Seq(mainClasses, helperClasses)
      mappings.map(_.to).distinct should contain only new File(outputDir, "lib/example.module.jar")
    }

    "keep implicit helper dependencies in the standalone ancestor" in {
      val plugin = node("plugin", SPackagingMethod.Standalone("lib/plugin.jar", static = false))
      val module = node("module", SPackagingMethod.PluginModule("example.module", static = false, embedded = true),
        parents = Seq(plugin), classRoots = Seq(new File("module/target/classes")))
      val helperClasses = new File("helper/target/classes")
      val helper = node("helper", SPackagingMethod.MergeIntoParent(), parents = Seq(module), classRoots = Seq(helperClasses))

      val mappings = new LinearMappingsBuilder(outputDir, PluginLogger).buildMappings(Seq(plugin, module, helper))

      mappings.filter(_.from == helperClasses).map(_.to) should contain only new File(outputDir, "lib/plugin.jar")
    }

    "read old structure snapshots without an embedded flag" in {
      val json = """{"type":"PluginModule","moduleName":"example.module","static":false}"""

      decode[SPackagingMethod](json) shouldBe Right(SPackagingMethod.PluginModule("example.module", static = false))
    }

    "preserve embedded modules in structure snapshots" in {
      val method: SPackagingMethod = SPackagingMethod.PluginModule("example.module", static = true, embedded = true)

      decode[SPackagingMethod](method.asJson.noSpaces) shouldBe Right(method)
    }
  }
}
