package org.jetbrains.sbtidea.download.jbr

import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.{ConsoleLogger, TmpDirUtils, pathToPathExt}
import org.scalatest.{FunSuite, Matchers}
import org.jetbrains.sbtidea.pathToPathExt
import sbt._


class JbrResolverTest extends FunSuite with Matchers with IdeaMock with TmpDirUtils with ConsoleLogger {

  test("extract jbr version from dependencies.txt") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrBintrayResolver()
    resolver.extractVersionFromIdea(ideaRoot) shouldBe Some("11_0_5b520.38")
  }

  test("jbr version major/minor split") {
    JbrBintrayResolver.splitVersion("11_0_5b520.38") shouldBe Some(("11_0_5", "520.38"))
  }

  // cannot compare entire url because it's platform-dependent
  test("jbr resolves to correct url") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrBintrayResolver()
    val artifacts = resolver.resolve(JbrDependency.apply(ideaRoot, IDEA_BUILDINFO))
    artifacts should not be empty
    artifacts.head.dlUrl.toString should include ("https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-jbr/")
    artifacts.head.dlUrl.toString should include ("b520.38")
    artifacts.head.dlUrl.toString should include ("jbr-11_0_5")
  }

}
