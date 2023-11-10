package org.jetbrains.sbtidea.download.plugin

import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.download.plugin.PluginDescriptor.Dependency
import org.jetbrains.sbtidea.{download, *}
import org.scalatest.Inside
import sbt.*

import java.nio.file.{Path, Paths}
import scala.language.implicitConversions

abstract class IntellijPluginResolverTestBase extends IntellijPluginInstallerTestBase with Inside {

  protected val pluginA: PluginDescriptor = PluginDescriptor("org.A", "VENDOR", "A - bundled", "0", "", "")
  protected val pluginB: PluginDescriptor = PluginDescriptor("org.B", "VENDOR", "B - remote", "0", "", "")
  protected val pluginC: PluginDescriptor = PluginDescriptor("org.C", "VENDOR", "C - remote", "0", "", "",
    Seq(Dependency("org.A", optional = true), Dependency("org.B", optional = false)))
  protected val pluginD: PluginDescriptor = PluginDescriptor("org.D", "VENDOR", "D - remote cyclic", "0", "", "",
    Seq(Dependency("org.E", optional = false), Dependency("org.A", optional = true)))
  protected val pluginE: PluginDescriptor = PluginDescriptor("org.E", "VENDOR", "C - remote cyclic", "0", "", "",
    Seq(Dependency("org.D", optional = false), Dependency("org.C", optional = true)))

  protected val descriptorMap: Map[String, PluginDescriptor] =
    Seq(pluginA, pluginB, pluginC, pluginD, pluginE).map(p => p.id -> p).toMap

  protected implicit def descriptor2Plugin(descriptor: PluginDescriptor): PluginDependency =
    PluginDependency(IntellijPlugin.Id(descriptor.id, None, None),
      IDEA_BUILDINFO,
      descriptor.dependsOn.map(p => plugin2PluginDep(p.id.toPlugin)))

  override protected implicit def localRegistry: LocalPluginRegistryApi = new LocalPluginRegistryApi {
    override def getPluginDescriptor(ideaPlugin: IntellijPlugin): Either[String, PluginDescriptor] = ideaPlugin match {
      case IntellijPlugin.Id(id, _, _, _) =>
        descriptorMap.get(id).filterNot(_.name.contains("remote")).toRight("plugin is remote")
      case IntellijPlugin.IdWithDownloadUrl(id, _) =>
        descriptorMap.get(id).filterNot(_.name.contains("remote")).toRight("plugin is remote")
      case IntellijPlugin.BundledFolder(name) =>
        descriptorMap.get(name).filterNot(_.name.contains("remote")).toRight("plugin is remote")
    }
    override def isPluginInstalled(ideaPlugin: IntellijPlugin): Boolean = ideaPlugin match {
      case withId: IntellijPlugin.WithKnownId =>
        descriptorMap.get(withId.id).exists(_.name.contains("bundled"))
      case IntellijPlugin.BundledFolder(name) =>
        descriptorMap.get(name).exists(_.name.contains("bundled"))
    }

    override def getAllDescriptors: Seq[PluginDescriptor] = descriptorMap.values.toSeq
    override def markPluginInstalled(ideaPlugin: IntellijPlugin, to: Path): Unit = ()
    override def getInstalledPluginRoot(ideaPlugin: IntellijPlugin): Path =
      Paths.get("INVALID")
  }

  override protected implicit def repoAPI: PluginRepoApi = new TestPluginRepoApi

  private class TestPluginRepoApi extends PluginRepoApi {
    override def getRemotePluginXmlDescriptor(idea: download.BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, PluginDescriptor] = {
      val descriptor = descriptorMap.get(pluginId).filter(_.name.contains("remote"))
      descriptor.toRight(new RuntimeException(s"TestPluginRepoApi error for getRemotePluginXmlDescriptor"))
    }

    override def getPluginDownloadURL(idea: download.BuildInfo, pluginInfo: IntellijPlugin.Id): URL =
      new URL("file:INVALID")

    override def getLatestPluginVersion(idea: download.BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, String] =
      throw new IllegalArgumentException
  }

}
