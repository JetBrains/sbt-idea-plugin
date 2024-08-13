package org.jetbrains.sbtidea.download.plugin

import org.apache.commons.io.FileUtils
import org.jetbrains.sbtidea.download.BuildInfo
import org.jetbrains.sbtidea.download.api.InstallContext
import org.jetbrains.sbtidea.productInfo.{ProductInfo, ProductInfoParser}
import org.jetbrains.sbtidea.{IntelliJPlatform, IntellijPlugin}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.featurespec.AnyFeatureSpecLike
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.nio.file.Files

class PluginRepoUtilsTest extends AnyFeatureSpecLike with BeforeAndAfterAll {

  private def createDummyProductInfoJson(buildNumber: String): String = {
    val productInfo = ProductInfo(
      name = "",
      version = "",
      versionSuffix = None,
      buildNumber = buildNumber,
      productCode = "",
      modules = Nil,
      launch = Nil,
      layout = Nil
    )
    ProductInfoParser.toJsonString(productInfo)
  }

  Feature("getPluginDownloadURL") {
    val buildInfo = BuildInfo("1.2.3", IntelliJPlatform.IdeaUltimate)

    def getUrlString(pluginInfo: IntellijPlugin.Id): String = {
      val baseDir = Files.createTempDirectory("PluginRepoUtilsTest_baseDir")
      val downloadDir = Files.createTempDirectory("PluginRepoUtilsTest_downloadDir")

      FileUtils.writeStringToFile(
        baseDir.resolve("product-info.json").toFile,
        createDummyProductInfoJson("11.22.33-actual"),
        "UTF-8"
      )

      try {
        val installContext = InstallContext(baseDir, downloadDir)
        val repoUtils = new PluginRepoUtils()(installContext)
        repoUtils.getPluginDownloadURL(buildInfo, pluginInfo).toString
      } finally {
        FileUtils.deleteDirectory(baseDir.toFile)
        FileUtils.deleteDirectory(downloadDir.toFile)
      }
    }

    Scenario("id") {
      getUrlString(IntellijPlugin.Id("plugin-id", None, None)) shouldBe "https://plugins.jetbrains.com/pluginManager?action=download&noStatistic=true&id=plugin-id&build=IU-11.22.33-actual"
    }

    Scenario("id with chanel") {
      getUrlString(IntellijPlugin.Id("plugin-id", None, Some("EAP"))) shouldBe "https://plugins.jetbrains.com/pluginManager?action=download&noStatistic=true&id=plugin-id&channel=EAP&build=IU-11.22.33-actual"
    }

    Scenario("id with version") {
      getUrlString(IntellijPlugin.Id("plugin-id", Some("4.5.6"), None)) shouldBe "https://plugins.jetbrains.com/plugin/download?noStatistic=true&pluginId=plugin-id&version=4.5.6"
    }

    Scenario("id with version and channel") {
      getUrlString(IntellijPlugin.Id("plugin-id", Some("4.5.6"), Some("EAP"))) shouldBe "https://plugins.jetbrains.com/plugin/download?noStatistic=true&pluginId=plugin-id&version=4.5.6&channel=EAP"
    }
  }
}
