package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.mappings.LinearMappingsBuilder
import org.jetbrains.sbtidea.packaging.structure.{PackagingMethod as SPackagingMethod}
import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.CapturingLogger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

//NOTE: the test was generated with Codex
class LinearMappingsBuilderMergeWarningsTest extends AnyWordSpec with Matchers with PackagingTestNodes {
  private val MergeWarningText = "will be merged into non-terminal"

  "LinearMappingsBuilder merge warnings" should {
    "skip warning when MergeIntoParent alternatives are only target ancestors" in {
      val ultimate = node("scalaUltimate", SPackagingMethod.Standalone("", static = false))
      val community = node("scalaCommunity", SPackagingMethod.Standalone("", static = false), parents = Seq(ultimate))
      val from = node("scala-impl", SPackagingMethod.MergeIntoParent(), parents = Seq(community))

      val messages = CapturingLogger.captureLog {
        new LinearMappingsBuilder(new File("target/test"), PluginLogger).buildMappings(Seq(from))
      }

      assertDoesNotContainWarningText(messages, MergeWarningText)
    }

    "emit warning when baseline MergeIntoParent setup gets one competing standalone branch" in {
      val ultimate = node("scalaUltimate", SPackagingMethod.Standalone("", static = false))
      val community = node("scalaCommunity", SPackagingMethod.Standalone("", static = false), parents = Seq(ultimate))
      val otherStandalone = node("other-standalone", SPackagingMethod.Standalone("", static = false))
      val bridge = node("bridge", SPackagingMethod.MergeIntoParent(), parents = Seq(otherStandalone))
      val from = node("scala-impl", SPackagingMethod.MergeIntoParent(), parents = Seq(community, bridge))

      val messages = CapturingLogger.captureLog {
        new LinearMappingsBuilder(new File("target/test"), PluginLogger).buildMappings(Seq(from))
      }

      assertContainsWarningText(messages, MergeWarningText)
      assertContainsWarningText(messages, "{other-standalone}")
    }

    "skip warning for explicit MergeIntoOther target" in {
      val ultimate = node("scalaUltimate", SPackagingMethod.Standalone("", static = false))
      val community = node("scalaCommunity", SPackagingMethod.Standalone("", static = false), parents = Seq(ultimate))
      val from = node("scala-impl", SPackagingMethod.MergeIntoOther(community), parents = Seq(community))

      val messages = CapturingLogger.captureLog {
        new LinearMappingsBuilder(new File("target/test"), PluginLogger).buildMappings(Seq(from))
      }

      assertDoesNotContainWarningText(messages, MergeWarningText)
    }

    "emit warning when scalaCommunity has a non-standalone parent and a competing standalone branch exists" in {
      val ultimate = node("scalaUltimate", SPackagingMethod.PluginModule("scala.ultimate", static = false))
      val community = node("scalaCommunity", SPackagingMethod.Standalone("", static = false), parents = Seq(ultimate))

      val scalaUltimateCandidate = node("scalaUltimateCandidate", SPackagingMethod.Standalone("", static = false))
      val bridge = node("bridge", SPackagingMethod.MergeIntoParent(), parents = Seq(scalaUltimateCandidate))
      val scalaImpl = node("scala-impl", SPackagingMethod.MergeIntoParent(), parents = Seq(community, bridge))

      val messages = CapturingLogger.captureLog {
        new LinearMappingsBuilder(new File("target/test"), PluginLogger).buildMappings(Seq(scalaImpl))
      }

      assertContainsWarningText(messages, MergeWarningText)
      assertContainsWarningText(messages, "{scalaUltimateCandidate}")
    }
  }

  private def assertContainsWarningText(messages: Seq[String], warningText: String): Unit =
    messages.exists(_.contains(warningText)) shouldBe true

  private def assertDoesNotContainWarningText(messages: Seq[String], warningText: String): Unit =
    messages.exists(_.contains(warningText)) shouldBe false
}
