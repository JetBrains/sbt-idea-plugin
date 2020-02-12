package org.jetbrains.sbtidea.download

import org.jetbrains.sbtidea.{ConsoleLogger, PluginLogger}
import org.scalatest.{FunSuite, Matchers}

import org.jetbrains.sbtidea.Keys._

class JbIdeaRepoArtifactResolverTest extends FunSuite with Matchers with IdeaMock with ConsoleLogger {

  private def createResolver: JBIdeaRepoArtifactResolver = new JBIdeaRepoArtifactResolver {
    override protected def log: PluginLogger = JbIdeaRepoArtifactResolverTest.this.log
  }

  test("latest eap is resolved") {
    val resolver = createResolver
    val result = resolver.resolveUrlForIdeaBuild(BuildInfo("LATEST-EAP-SNAPSHOT", IntelliJPlatform.IdeaCommunity, None))
    result should not be empty
  }

  test("latest 192 release is resolved") {
    val resolver = createResolver
    val result = resolver.resolveUrlForIdeaBuild(BuildInfo("192.6603.28", IntelliJPlatform.IdeaCommunity, None))
    result should not be empty
  }

  test("latest 191 release is resolved") {
    val resolver = createResolver
    val result = resolver.resolveUrlForIdeaBuild(BuildInfo("191.7479.19", IntelliJPlatform.IdeaCommunity, None))
    result should not be empty
  }


}
