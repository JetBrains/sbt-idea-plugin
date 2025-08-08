package org.jetbrains.sbtidea.tasks.classpath

import org.jetbrains.sbtidea.download.plugin.PluginDescriptor
import org.jetbrains.sbtidea.{PluginJars, Keys as SbtIdeaKeys}
import sbt.Keys.*
import sbt.internal.inc.Analysis
import sbt.util.InterfaceUtil
import sbt.{Def, *}
import xsbti.compile.CompileAnalysis

import java.io.File
import scala.collection.mutable

object AttributedClasspathTasks {

  import Artifacts.*

  private val INTELLIJ_SDK_ARTIFACT_NAME = "INTELLIJ-SDK"
  private val INTELLIJ_SDK_TEST_ARTIFACT_NAME: String = "INTELLIJ-SDK-TEST"

  // These special classifiers have a special handling logic in Scala plugin and DevKit plugin
  // See org.jetbrains.sbt.project.SbtProjectResolver.IJ_SDK_CLASSIFIERS
  // See https://youtrack.jetbrains.com/issue/SCL-17415 (though it doesn't have many details)
  // NOTE: it seems like it's some legacy and DevKit doesn't actually need this, but it's not 100%
  // same classifier is used for both main and test jars
  private val IJ_SDK_CLASSIFIER = "IJ-SDK"
  private val IJ_PLUGIN_CLASSIFIER = "IJ-PLUGIN"

  object Artifacts {
    private val intellijMainJarsHashToArtifactSuffix = mutable.Map[Int, String]()

    /**
     * Sometimes different modules might contain a different set of intellijMainJars.<br>
     * (it better shouldn't be so, but it might occur accidentally, e.g., due to misconfiguration)<br>
     * In this case, we want to create different libraries.<br>
     * For this we need unique names
     *
     * @return 1. empty string in the simplest case, when all modules depend on the same set of jars from the platform<br>
     *         2. jars hash code otherwise
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
      //should be empty in simple cases
      val artifactSuffix = getUniqueArtifactSuffix(intellijMainJarsValue, intellijMainJarsHashToArtifactSuffix)
      Artifact(name = INTELLIJ_SDK_ARTIFACT_NAME + artifactSuffix, classifier = IJ_SDK_CLASSIFIER)
    }

    val ideaTestArtifact: Artifact =
      Artifact(name = INTELLIJ_SDK_TEST_ARTIFACT_NAME, classifier = IJ_SDK_CLASSIFIER)

    val ideaSourcesArtifact: Artifact =
      Artifact(name = INTELLIJ_SDK_ARTIFACT_NAME, `type` = Artifact.SourceType, extension = "zip", classifier = IJ_SDK_CLASSIFIER)

    val ideaTestSourcesArtifact: Artifact =
      Artifact(name = INTELLIJ_SDK_TEST_ARTIFACT_NAME, `type` = Artifact.SourceType, extension = "zip", classifier = IJ_SDK_CLASSIFIER)


    def pluginArtifact(descriptor: PluginDescriptor): Artifact =
      Artifact(name = s"IJ-PLUGIN[${descriptor.id}]", classifier = IJ_PLUGIN_CLASSIFIER)

    def pluginSourcesArtifact(pluginName: String): Artifact =
      Artifact(name = pluginName, `type` = Artifact.SourceType, extension = "zip", classifier = IJ_PLUGIN_CLASSIFIER)
  }

  object Modules {
    def getIntellijSdkModule(buildNumber: String): ModuleID =
      "org.jetbrains" % INTELLIJ_SDK_ARTIFACT_NAME % buildNumber withSources()

    def getIntellijSdkTestModule(buildNumber: String): ModuleID =
      "org.jetbrains" % INTELLIJ_SDK_TEST_ARTIFACT_NAME % buildNumber % Test withSources()
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
    val module = Modules.getIntellijSdkModule(buildNumber)
    buildAttributedClasspath(jars)(artifact, module, Compile)
  }

  private def buildTestJarsAttributedClassPath(
    jars: Seq[File],
    buildNumber: String
  ): Classpath = {
    val artifact = ideaTestArtifact
    val module = Modules.getIntellijSdkTestModule(buildNumber)
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

  /**
   * Defined as a task because it needs to be called for each subproject which is a `"test->test;compile->compile"`
   * dependency of the root sbt subproject which hosts the IDEA plugin.
   *
   * @note This implementation is written to mimic `sbt.internal.ClasspathImpl.trackedExportedJarProducts`
   *       and the private task definition `sbt.internal.ClasspathImpl.trackedNonJarProductsImplTask`.
   */
  def attributedClassDirectory: Def.Initialize[Task[Attributed[File]]] = Def.task {
    val art = (artifact in packageBin).value
    val module = projectID.value
    val config = configuration.value
    val dir = classDirectory.value
    val analysis = InterfaceUtil.toOption(previousCompile.value.analysis()).getOrElse(Analysis.empty)
    val optURL = apiURL.value
    storeApi(analyzed(dir, analysis), optURL)
      .put(artifact.key, art)
      .put(moduleID.key, module)
      .put(configuration.key, config)
  }

  /**
   * A copy of [[sbt.internal.ClasspathImpl.analyzed]] which we cannot call from here as it is package private.
   */
  private def analyzed[A](data: A, analysis: CompileAnalysis): Attributed[A] =
    Attributed.blank(data).put(Keys.analysis, analysis)

  /**
   * A copy of [[sbt.internal.APIMappings.store]] which we cannot call from here as it is package private.
   */
  private def storeApi[T](attr: Attributed[T], entryAPI: Option[URL]): Attributed[T] = entryAPI match {
    case None => attr
    case Some(u) => attr.put(Keys.entryApiURL, u)
  }
}
