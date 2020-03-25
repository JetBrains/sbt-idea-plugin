package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.scalatest.{FunSuite, Matchers}

class SearchPluginIdTest extends FunSuite with IdeaMock with Matchers {

  test("testApply") {
    val searcher = new SearchPluginId(installIdeaMock, IDEA_BUILDINFO, useRemote = false)
    searcher("prop") should not be empty
  }

  test("marketplace searching") {
    val searcher = new SearchPluginId(installIdeaMock, IDEA_BUILDINFO, useRemote = true, useBundled = false)
    searcher("Scala") should not be empty
  }

}
