package org.jetbrains.sbtidea.tasks.classpath

import org.jetbrains.sbtidea.{PluginJars, Keys as SbtIdeaKeys}
import org.jetbrains.sbtidea.download.plugin.PluginDescriptor
import sbt.Keys.*
import sbt.{Def, *}

import java.io.File
import scala.collection.mutable

object AttributedClasspathTasks {

  import Artifacts.*
  import Modules.*

  private val INTELLIJ_SDK_ARTIFACT_NAME = "INTELLIJ-SDK"
  private val INTELLIJ_SDK_TEST_ARTIFACT_NAME: String = "INTELLIJ-SDK-TEST"

  object Artifacts {
    private val intellijMainJarsHashToArtifactSuffix = mutable.Map[Int, String]()

    /**
     * Sometimes different modules might contain a different set of intellijMainJars.<br>
     * (it better shouldn't be so, but it might occur accidentally, e.g., due to misconfiguration)<br>
     * In this case, we want to create different libraries.<br>
     * For this we need unique names
     */
    private def getUniqueArtifactSuffix(jars: Seq[File], cache: mutable.Map[Int, String]): String =
      cache.synchronized {
        val jarsHash = jars.hashCode() //includes hash codes of all files
        cache.getOrElseUpdate(jarsHash, {
          val alreadyExistingArtifactsWithSameName = cache.size
          if (alreadyExistingArtifactsWithSameName == 0) ""
          else s"-$alreadyExistingArtifactsWithSameName"
        })
      }

    def ideaMainJarsArtifact(intellijMainJarsValue: Seq[File]): Artifact = {
      val artifactSuffix = getUniqueArtifactSuffix(intellijMainJarsValue, intellijMainJarsHashToArtifactSuffix)
      Artifact(INTELLIJ_SDK_ARTIFACT_NAME, s"IJ-SDK$artifactSuffix")
    }

    val ideaTestArtifact: Artifact =
      Artifact(INTELLIJ_SDK_TEST_ARTIFACT_NAME, "IJ-SDK-TEST")

    val ideaSourcesArtifact: Artifact =
      Artifact(INTELLIJ_SDK_ARTIFACT_NAME, Artifact.SourceType, "zip", "IJ-SDK")

    def pluginArtifact(descriptor: PluginDescriptor): Artifact =
      Artifact(s"IJ-PLUGIN[${descriptor.id}]", "IJ-PLUGIN")

    def pluginSourcesArtifact(pluginName: String): Artifact =
      Artifact(pluginName, Artifact.SourceType, "zip", Artifact.SourceClassifier)
  }

  object Modules {
    def getIntellijSdkModule(buildNumber: String): ModuleID =
      "org.jetbrains" % INTELLIJ_SDK_ARTIFACT_NAME % buildNumber withSources()

    def getIntellijSdkTestModule(buildNumber: String): ModuleID =
      "org.jetbrains" % INTELLIJ_SDK_TEST_ARTIFACT_NAME % buildNumber % Test
  }

  //======= ATTRIBUTED CLASSPATH =======
  def main: Def.Initialize[Task[Classpath]] = Def.task {
    val buildNumber: String = SbtIdeaKeys.productInfo.in(ThisBuild).value.buildNumber
    val jars: Seq[File] = SbtIdeaKeys.intellijMainJars.value
    buildMainJarsAttributedClassPath(jars, buildNumber)
  }

  def test: Def.Initialize[Task[Classpath]] = Def.task {
    val buildNumber: String = SbtIdeaKeys.productInfo.in(ThisBuild).value.buildNumber
    val jars: Seq[File] = SbtIdeaKeys.intellijTestJars.value
    buildTestJarsAttributedClassPath(jars, buildNumber)
  }

  private def buildMainJarsAttributedClassPath(
    jars: Seq[File],
    buildNumber: String
  ): Classpath = {
    val artifact = ideaMainJarsArtifact(jars)
    val module = getIntellijSdkModule(buildNumber)
    buildAttributedClasspath(jars)(artifact, module, Compile)
  }

  private def buildTestJarsAttributedClassPath(
    jars: Seq[File],
    buildNumber: String
  ): Classpath = {
    val artifact = ideaTestArtifact
    val module = getIntellijSdkTestModule(buildNumber)
    buildAttributedClasspath(jars)(artifact, module, Test)
  }

  def plugins: Def.Initialize[Task[Seq[(PluginDescriptor, Classpath)]]] = Def.task {
    val plugins = SbtIdeaKeys.intellijPluginJars.value
    val addSources = SbtIdeaKeys.intellijAttachSources.in(Global).value
    buildPluginsAttributedClassPath(plugins, addSources)
  }

  def extraRuntimePluginsInTests: Def.Initialize[Task[Seq[(PluginDescriptor, Classpath)]]] = Def.task {
    val plugins = SbtIdeaKeys.intellijExtraRuntimePluginsJarsInTests.value
    val addSources = SbtIdeaKeys.intellijAttachSources.in(Global).value
    buildPluginsAttributedClassPath(plugins, addSources)
  }

  private def buildPluginsAttributedClassPath(
    plugins: Seq[PluginJars],
    addSources: Boolean
  ): Seq[(PluginDescriptor, Classpath)] = {
    plugins.map { case PluginJars(descriptor, _, jars) =>
      val classpath = AttributedClasspathTasks.buildPluginAttributedClassPath(descriptor, jars, addSources)
      descriptor -> classpath
    }
  }

  private def buildPluginAttributedClassPath(
    descriptor: PluginDescriptor,
    pluginJars: Seq[File],
    addSources: Boolean
  ): Classpath = {
    val artifact = pluginArtifact(descriptor)
    val module0 = descriptor.vendor % descriptor.id % descriptor.version
    val module = if (addSources) module0.withSources() else module0
    buildAttributedClasspath(pluginJars)(artifact, module, Compile)
  }

  private def buildAttributedClasspath(jars: Seq[File])(
    artifact: Artifact,
    module: ModuleID,
    configuration: librarymanagement.Configuration
  ): Classpath = {
    val attributes: AttributeMap = AttributeMap.empty
      .put(Keys.artifact.key, artifact)
      .put(Keys.moduleID.key, module)
      .put(Keys.configuration.key, configuration)
    jars.map(Attributed(_)(attributes))
  }
}
