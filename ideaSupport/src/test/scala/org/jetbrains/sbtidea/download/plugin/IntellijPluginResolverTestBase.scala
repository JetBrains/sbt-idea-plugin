package org.jetbrains.sbtidea.download.plugin

import java.nio.file.{Path, Paths}

import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.download.plugin.PluginDescriptor.Dependency
import org.jetbrains.sbtidea.{Keys, download, pathToPathExt}
import org.scalatest.Inside
import sbt._

import scala.language.implicitConversions

abstract class IntellijPluginResolverTestBase extends IntellijPluginInstallerTestBase with Inside {

  protected val pluginA: PluginDescriptor = PluginDescriptor("org.A", "A - bundled", "0", "", "")
  protected val pluginB: PluginDescriptor = PluginDescriptor("org.B", "B - remote", "0", "", "")
  protected val pluginC: PluginDescriptor = PluginDescriptor("org.C", "C - remote", "0", "", "",
    Seq(Dependency("org.A", optional = true), Dependency("org.B", optional = false)))
  protected val pluginD: PluginDescriptor = PluginDescriptor("org.D", "D - remote cyclic", "0", "", "",
    Seq(Dependency("org.E", optional = false), Dependency("org.A", optional = true)))
  protected val pluginE: PluginDescriptor = PluginDescriptor("org.E", "C - remote cyclic", "0", "", "",
    Seq(Dependency("org.D", optional = false), Dependency("org.C", optional = true)))

  protected val descriptorMap: Map[String, PluginDescriptor] =
    Seq(pluginA, pluginB, pluginC, pluginD, pluginE).map(p => p.id -> p).toMap

  protected implicit def descriptor2Plugin(descriptor: PluginDescriptor): PluginDependency =
      PluginDependency(Keys.IntellijPlugin.Id(descriptor.id, None, None),
        IDEA_BUILDINFO,
        descriptor.dependsOn.map(p => plugin2PluginDep(p.id.toPlugin)))

  override protected implicit val localRegistry: LocalPluginRegistryApi = new LocalPluginRegistryApi {
    override def getPluginDescriptor(ideaPlugin: Keys.IntellijPlugin): Either[String, PluginDescriptor] = ideaPlugin match {
      case IntellijPlugin.Url(_) =>
        throw new IllegalArgumentException("url plugin not supported")
      case IntellijPlugin.Id(id, _, _) =>
        descriptorMap.get(id).filterNot(_.name.contains("remote")).toRight("plugin is remote")
      case IntellijPlugin.BundledFolder(name) =>
        descriptorMap.get(name).filterNot(_.name.contains("remote")).toRight("plugin is remote")
    }
    override def isPluginInstalled(ideaPlugin: Keys.IntellijPlugin): Boolean = ideaPlugin match {
      case IntellijPlugin.Url(_) => false
      case IntellijPlugin.Id(id, _, _) =>
        descriptorMap.get(id).exists(_.name.contains("bundled"))
      case IntellijPlugin.BundledFolder(name) =>
        descriptorMap.get(name).exists(_.name.contains("bundled"))
    }
    override def markPluginInstalled(ideaPlugin: Keys.IntellijPlugin, to: Path): Unit = ()
    override def getInstalledPluginRoot(ideaPlugin: Keys.IntellijPlugin): Path =
      Paths.get("INVALID")
  }

  override protected implicit val repoAPI: PluginRepoApi = new PluginRepoApi {
    override def getRemotePluginXmlDescriptor(idea: download.BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, PluginDescriptor] =
      descriptorMap.get(pluginId).filter(_.name.contains("remote")).toRight(null)
    override def getPluginDownloadURL(idea: download.BuildInfo, pluginInfo: Keys.IntellijPlugin.Id): URL =
      new URL("file:INVALID")
    override def getLatestPluginVersion(idea: download.BuildInfo, pluginId: String, channel: Option[String]): Either[Throwable, String] =
      throw new IllegalArgumentException
  }

}
