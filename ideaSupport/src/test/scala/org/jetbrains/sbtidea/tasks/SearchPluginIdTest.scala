package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.CapturingLogger.captureLogAndValue
import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.tasks.SearchPluginId.PluginBasicInfo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SearchPluginIdTest extends AnyFunSuite with IdeaMock with Matchers {

  test("search plugin: local only") {
    val ideaRoot = installIdeaMock
    val (logText, actual) = captureLogAndValue() {
      val searcher = new SearchPluginId(ideaRoot, IDEA_BUILDINFO, useRemote = false, useBundled = true)
      searcher.search("prop")
    }

    actual shouldBe Seq(PluginBasicInfo("com.intellij.properties", "Properties", isRemote = false))

    logText should contain("[info] Plugin ids from plugins_index.xml: com.intellij.properties, com.jetbrains.codeWithMe, org.jetbrains.plugins.yaml")
  }

  test("search plugin: marketplace only") {
    val ideaRoot = installIdeaMock

    val searcher = new SearchPluginId(ideaRoot, IDEA_BUILDINFO, useRemote = true, useBundled = false)
    val actual = searcher.search("Scala")

    actual should contain(PluginBasicInfo("org.intellij.scala", "Scala", isRemote = true))
  }
}
