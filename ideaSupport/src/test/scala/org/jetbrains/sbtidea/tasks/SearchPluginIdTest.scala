package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SearchPluginIdTest extends AnyFunSuite with IdeaMock with Matchers {

  test("search plugin: local only") {
    val ideaRoot = installIdeaMock
    val searcher = new SearchPluginId(ideaRoot, IDEA_BUILDINFO, useRemote = false, useBundled = true)
    val actual = searcher("prop")
    actual should not be empty
  }

  test("search plugin: marketplace only") {
    val ideaRoot = installIdeaMock
    val searcher = new SearchPluginId(ideaRoot, IDEA_BUILDINFO, useRemote = true, useBundled = false)
    val actual = searcher("Scala")
    actual should not be empty
  }

}
