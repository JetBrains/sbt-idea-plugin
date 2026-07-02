package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.download.BuildInfo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.annotation.nowarn
import scala.language.implicitConversions

class JbIdeaRepoArtifactResolverTest extends AnyFunSuite with Matchers with IdeaMock {

  private def createResolver: IJRepoIdeaResolver = new IJRepoIdeaResolver

  private implicit def buildInfo2Dep(buildInfo: BuildInfo): IdeaDependency = IdeaDependency(buildInfo)

  private def latestEapUrls(platform: IntelliJPlatform): Seq[String] =
    createResolver
      .resolve(BuildInfo(BuildInfo.LATEST_EAP_SNAPSHOT, platform))
      .map(_.dlUrl.toString)

  test("latest eap is resolved") {
    val resolver = createResolver
    val result = resolver.resolve(BuildInfo("LATEST-EAP-SNAPSHOT", IntelliJPlatform.Idea))
    result should not be empty
  }

  test("latest 192 release is resolved") {
    val resolver = createResolver
    val result = resolver.resolve(BuildInfo("192.6603.28", IntelliJPlatform.Idea))
    result should not be empty
  }

  test("latest 191 release is resolved") {
    val resolver = createResolver
    val result = resolver.resolve(BuildInfo("191.7479.19", IntelliJPlatform.Idea))
    result should not be empty
  }

  test("Idea resolves ideaIU distribution and sources") {
    val urls = latestEapUrls(IntelliJPlatform.Idea)

    urls should have size 2
    urls.head should include ("/com/jetbrains/intellij/idea/ideaIU/LATEST-EAP-SNAPSHOT/ideaIU-LATEST-EAP-SNAPSHOT.zip")
    urls(1) should include ("/com/jetbrains/intellij/idea/ideaIU/LATEST-EAP-SNAPSHOT/ideaIU-LATEST-EAP-SNAPSHOT-sources.jar")
  }

  test("PyCharm resolves pycharmPY distribution") {
    val urls = latestEapUrls(IntelliJPlatform.PyCharm)

    urls should not be empty
    urls.head should include ("/com/jetbrains/intellij/pycharm/pycharmPY/LATEST-EAP-SNAPSHOT/pycharmPY-LATEST-EAP-SNAPSHOT.zip")
  }

  test("deprecated aliases preserve old artifact coordinates") {
    val deprecatedPlatforms = Seq[(IntelliJPlatform, String)](
      IntelliJPlatform.IdeaCommunity -> "ideaIC",
      IntelliJPlatform.IdeaUltimate -> "ideaIU",
      IntelliJPlatform.PyCharmCommunity -> "pycharmPC",
      IntelliJPlatform.PyCharmProfessional -> "pycharmPY",
    ): @nowarn("cat=deprecation")

    for ((platform, artifactId) <- deprecatedPlatforms) {
      val descriptor = IntellijVersionUtils.detectArtifactLocation(BuildInfo(BuildInfo.LATEST_EAP_SNAPSHOT, platform), ".zip")
      val url = descriptor.url.toString
      url should include (s"/$artifactId/LATEST-EAP-SNAPSHOT/$artifactId-LATEST-EAP-SNAPSHOT.zip")
    }
  }

}
