package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SearchPluginIdTest extends AnyFunSuite with IdeaMock with Matchers {

  test("testApply") {
    val searcher = new SearchPluginId(installIdeaMock, IDEA_BUILDINFO, useRemote = false)
    searcher("prop") should not be empty
  }

  test("marketplace searching") {
    val searcher = new SearchPluginId(installIdeaMock, IDEA_BUILDINFO, useRemote = true, useBundled = false)
    searcher("Scala") should not be empty
  }

}
