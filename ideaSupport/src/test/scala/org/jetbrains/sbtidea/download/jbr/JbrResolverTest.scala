package org.jetbrains.sbtidea.download.jbr

import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.{ConsoleLogger, NoJbr, TmpDirUtils, pathToPathExt}
import org.scalatest.{FunSuite, Matchers}
import sbt._


class JbrResolverTest extends FunSuite with Matchers with IdeaMock with TmpDirUtils with ConsoleLogger {

  test("extract jbr version from dependencies.txt") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrBintrayResolver()
    resolver.extractVersionFromIdea(ideaRoot) shouldBe Some("11_0_10b1304.1")
  }

  test("jbr version major/minor split") {
    JbrBintrayResolver.splitVersion("11_0_5b520.38") shouldBe Some(("11_0_5", "520.38"))
  }

  // cannot compare entire url because it's platform-dependent
  test("jbr resolves to correct url") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrBintrayResolver()
    val artifacts = resolver.resolve(JbrDependency(ideaRoot, IDEA_BUILDINFO, JBR_INFO))
    artifacts should not be empty
    artifacts.head.dlUrl.toString should include ("https://cache-redirector.jetbrains.com/intellij-jbr/")
    artifacts.head.dlUrl.toString should include ("b1304.1")
    artifacts.head.dlUrl.toString should include ("jbr_dcevm-11_0_10")
  }

  test("NoJbr jbrInfo resolves to 0 artifacts") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrBintrayResolver()
    val results = resolver.resolve(JbrDependency(ideaRoot, IDEA_BUILDINFO, NoJbr))
    results shouldBe empty
  }

}
