package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.ConsoleLogger
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.download.BuildInfo
import org.scalatest.{FunSuite, Matchers}

import scala.language.implicitConversions

class JbIdeaRepoArtifactResolverTest extends FunSuite with Matchers with IdeaMock with ConsoleLogger {

  private def createResolver: IJRepoIdeaResolver = new IJRepoIdeaResolver

  private implicit def buildInfo2Dep(buildInfo: BuildInfo): IdeaDependency = IdeaDependency(buildInfo)

  test("latest eap is resolved") {
    val resolver = createResolver
    val result = resolver.resolve(BuildInfo("LATEST-EAP-SNAPSHOT", IntelliJPlatform.IdeaCommunity, None))
    result should not be empty
  }

  test("latest 192 release is resolved") {
    val resolver = createResolver
    val result = resolver.resolve(BuildInfo("192.6603.28", IntelliJPlatform.IdeaCommunity, None))
    result should not be empty
  }

  test("latest 191 release is resolved") {
    val resolver = createResolver
    val result = resolver.resolve(BuildInfo("191.7479.19", IntelliJPlatform.IdeaCommunity, None))
    result should not be empty
  }


}
