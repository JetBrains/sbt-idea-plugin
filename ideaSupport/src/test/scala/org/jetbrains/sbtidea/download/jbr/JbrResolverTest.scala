package org.jetbrains.sbtidea.download.jbr

import org.jetbrains.sbtidea.download.idea.IdeaMock
import org.jetbrains.sbtidea.{JbrVersion, NoJbr, TmpDirUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JbrResolverTest extends AnyFunSuite with Matchers with IdeaMock with TmpDirUtils {

  test("extract jbr version from dependencies.txt") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrResolver()
    resolver.extractVersionFromIdea(ideaRoot) shouldBe Some("21.0.3b458.1")
  }

  test("jbr version major/minor split") {
    JbrVersion.parse("11_0_5b520.38") shouldBe JbrVersion("11_0_5", "520.38")
  }

  // cannot compare entire url because it's platform-dependent
  test("jbr resolves to correct url") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrResolver()
    val artifacts = resolver.resolve(JbrDependency(ideaRoot, IDEA_BUILDINFO, JBR_INFO))
    artifacts should not be empty
    artifacts.head.dlUrl.toString should include ("https://cache-redirector.jetbrains.com/intellij-jbr/")
    artifacts.head.dlUrl.toString should include ("b458.1")
    artifacts.head.dlUrl.toString should include ("jbr_jcef-21.0.3")
  }

  test("NoJbr jbrInfo resolves to 0 artifacts") {
    val ideaRoot = installIdeaMock
    val resolver = new JbrResolver()
    val results = resolver.resolve(JbrDependency(ideaRoot, IDEA_BUILDINFO, NoJbr))
    results shouldBe empty
  }

}
