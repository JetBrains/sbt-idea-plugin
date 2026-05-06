package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.CapturingLogger.captureLog
import org.jetbrains.sbtidea.download.api.IdeInstallationContext

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}

final class ProvidedModuleNamesCacheTest extends IntellijPluginInstallerTestBase {

  test("provided module names are calculated once for the same IDE installation key") {
    withProvidedModuleNamesCacheDebugEnabled {
      val messages = captureLog {
        implicit val ctx: IdeInstallationContext = installContext

        val firstResult = ProvidedModuleNamesCache.providedModuleNames(ctx.productInfo)
        val secondResult = ProvidedModuleNamesCache.providedModuleNames(ctx.productInfo)
        val thirdResult = ProvidedModuleNamesCache.providedModuleNames(ctx.productInfo)

        withClue(s"Expected provided module names to include intellij.java.backend. Actual modules: ${firstResult.toSeq.sorted.mkString(", ")}") {
          firstResult should contain("intellij.java.backend")
        }
        withClue(s"Expected the second lookup for the same mock IDE root to reuse the first cached result. first=$firstResult, second=$secondResult") {
          secondResult shouldBe firstResult
        }
        withClue(s"Expected the third lookup for the same mock IDE root to reuse the first cached result. first=$firstResult, third=$thirdResult") {
          thirdResult shouldBe firstResult
        }
      }

      assertCalculationMessageCount(
        messages = messages,
        expectedCount = 1,
        clue = "Expected exactly one provided-module-names calculation marker for repeated lookups of the same mock IDE key.",
      )
    }
  }

  test("provided module names are recalculated when product-info.json stamp changes") {
    assertCacheRecalculatedAfterStampChange("product-info.json last-modified time changes") { ideRoot =>
      bumpLastModified(ideRoot.resolve("product-info.json"))
    }
  }

  test("provided module names are recalculated when module-descriptors.jar stamp changes") {
    assertCacheRecalculatedAfterStampChange("modules/module-descriptors.jar last-modified time changes") { ideRoot =>
      bumpLastModified(ideRoot.resolve("modules").resolve("module-descriptors.jar"))
    }
  }

  test("provided module names are recalculated when bundled plugin jar directory stamp changes") {
    assertCacheRecalculatedAfterStampChange("a bundled plugin lib/modules directory last-modified time changes") { ideRoot =>
      val modulesDirectory = ideRoot.resolve("plugins").resolve("java").resolve("lib").resolve("modules")
      Files.createFile(modulesDirectory.resolve("stamp-test.jar"))
      bumpLastModified(modulesDirectory)
    }
  }

  private def assertCacheRecalculatedAfterStampChange(stampChangeDescription: String)(changeStamp: Path => Unit): Unit = {
    withProvidedModuleNamesCacheDebugEnabled {
      val messages = captureLog {
        implicit val ctx: IdeInstallationContext = installContext
        val productInfo = ctx.productInfo

        val firstResult = ProvidedModuleNamesCache.providedModuleNames(productInfo)
        changeStamp(ctx.baseDirectory)
        val secondResult = ProvidedModuleNamesCache.providedModuleNames(productInfo)

        withClue(
          s"""Expected stamp-only IDE change to keep the provided module names result stable.
             |Stamp change: $stampChangeDescription
             |Before: ${firstResult.toSeq.sorted.mkString(", ")}
             |After: ${secondResult.toSeq.sorted.mkString(", ")}""".stripMargin
        ) {
          secondResult shouldBe firstResult
        }
      }

      assertCalculationMessageCount(
        messages = messages,
        expectedCount = 2,
        clue =
          s"Expected the cache to calculate once before and once after stamp change: $stampChangeDescription",
      )
    }
  }

  private def bumpLastModified(path: Path): Unit = {
    val currentMillis = Files.getLastModifiedTime(path).toMillis
    Files.setLastModifiedTime(path, FileTime.fromMillis(currentMillis + 60000L))
  }

  private def assertCalculationMessageCount(messages: Seq[String], expectedCount: Int, clue: String): Unit = {
    val calculationMessages = messages.filter(_.contains(ProvidedModuleNamesCache.CalculatingProvidedModuleNamesMessagePrefix))
    withClue(
      s"""$clue
         |Expected marker count: $expectedCount
         |Actual marker count: ${calculationMessages.size}
         |Matching messages:
         |${calculationMessages.mkString("\n")}
         |All messages:
         |${messages.mkString("\n")}""".stripMargin
    ) {
      calculationMessages.size shouldBe expectedCount
    }
  }

  private def withProvidedModuleNamesCacheDebugEnabled[T](body: => T): T = {
    val property = ProvidedModuleNamesCache.DebugProperty
    val previousValue = Option(System.getProperty(property))
    System.setProperty(property, "true")
    try {
      body
    } finally {
      previousValue match {
        case Some(value) => System.setProperty(property, value)
        case None => System.clearProperty(property)
      }
    }
  }
}
