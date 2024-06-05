package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.{intellijAttachSources, intellijBaseDirectory, intellijMainJars, intellijPluginJars, intellijTestJars, productInfo}
import org.jetbrains.sbtidea.download.idea.IdeaSourcesImpl
import sbt.Keys.*
import sbt.{Def, librarymanagement, *}

import scala.collection.mutable

object UpdateWithIDEAInjectionTask extends SbtIdeaTask[UpdateReport] {

  private val intellijMainJarsHashToArtifactSuffix = new mutable.ListMap[Int, String]

  private val INTELLIJ_SDK_ARTIFACT_NAME = "INTELLIJ-SDK"
  private val INTELLIJ_SDK_TEST_ARTIFACT_NAME: String = "INTELLIJ-SDK-TEST"

  private def getIdeaMainJarsArtifact(intellijMainJarsValue: Classpath): Artifact =
    intellijMainJarsHashToArtifactSuffix.synchronized {
      //Sometimes different modules might contain a different set of intellijMainJars
      //(it better shouldn't be so, but it might occur accidentally, e.g., due to misconfiguration)
      //In this case, we want to create different libraries.
      //For this we need unique names
      val jarsHash = intellijMainJarsValue.hashCode() //includes hash codes of all files
      val artifactSuffix = intellijMainJarsHashToArtifactSuffix.getOrElseUpdate(jarsHash, {
        val alreadyExistingArtifactsWithSameName = intellijMainJarsHashToArtifactSuffix.size
        if (alreadyExistingArtifactsWithSameName == 0) ""
        else s"-$alreadyExistingArtifactsWithSameName"
      })

      Artifact(INTELLIJ_SDK_ARTIFACT_NAME, s"IJ-SDK$artifactSuffix")
    }

  private val ideaSourcesArtifact: Artifact =
    Artifact(INTELLIJ_SDK_ARTIFACT_NAME, Artifact.SourceType, "zip", "IJ-SDK")

  private val ideaTestArtifact: Artifact =
    Artifact(INTELLIJ_SDK_TEST_ARTIFACT_NAME, "IJ-SDK-TEST")

  private def getIntellijSdkModule(buildNumber: String): ModuleID =
    "org.jetbrains" % INTELLIJ_SDK_ARTIFACT_NAME % buildNumber withSources()

  private def getIntellijSdkTestFrameworkModule(buildNumber: String): ModuleID =
    "org.jetbrains" % INTELLIJ_SDK_TEST_ARTIFACT_NAME % buildNumber % Test

  override def createTask: Def.Initialize[Task[sbt.UpdateReport]] = Def.task {
    val intellijBaseDir = intellijBaseDirectory.value

    val intellijMainJarsValue = intellijMainJars.value
    val intellijTestJarsValue = intellijTestJars.value

    val attachSources = intellijAttachSources.in(Global).value

    val buildNumber = productInfo.in(ThisBuild).value.buildNumber
    val ideaModule = getIntellijSdkModule(buildNumber)
    val ideaModuleTest = getIntellijSdkTestFrameworkModule(buildNumber)

    val intelliJSourcesArchive  = intellijBaseDir / IdeaSourcesImpl.SOURCES_ZIP
    val ideaArtifacts           =
      intellijMainJarsValue.map(getIdeaMainJarsArtifact(intellijMainJarsValue) -> _.data) ++
        (if (attachSources)  Seq(ideaSourcesArtifact -> intelliJSourcesArchive)
        else                Seq.empty)
    val ideaTestArtifacts           =
      intellijTestJarsValue.map(ideaTestArtifact -> _.data)

    val originalReport = update.value
    val reportWithIdeaMainJars = injectIntoUpdateReport(originalReport, Configurations.Compile, ideaArtifacts, ideaModule)
    val reportWithIdeaTestJars = injectIntoUpdateReport(reportWithIdeaMainJars, Configurations.Test, ideaTestArtifacts, ideaModuleTest)

    val pluginClassPaths = intellijPluginJars.value
    val finalReport = pluginClassPaths.foldLeft(reportWithIdeaTestJars) { case (report, (_, classpath)) =>
      classpath.headOption.map { f =>
        val pluginModule = f.get(moduleID.key).get
        val pluginArtifact = f.get(artifact.key).get
        // bundled plugin has the same version as the platform; add sources
        if (attachSources && pluginModule.revision == buildNumber) {
          val pluginArtifacts = classpath.map(pluginArtifact -> _.data) :+
            Artifact(pluginArtifact.name, Artifact.SourceType, "zip", Artifact.SourceClassifier) -> intelliJSourcesArchive
          injectIntoUpdateReport(report, Configurations.Compile, pluginArtifacts, pluginModule)
        } else {
          val pluginArtifacts = classpath.map(pluginArtifact -> _.data)
          injectIntoUpdateReport(report, Configurations.Compile, pluginArtifacts, pluginModule)
        }
      }.getOrElse(report)
    }
    finalReport
  }

  private def injectIntoUpdateReport(
    report: UpdateReport,
    injectInto: Configuration,
    artifacts: Seq[(Artifact, File)],
    module: ModuleID
  ): UpdateReport = {
    val newConfigurationReports = report.configurations.map { report =>
      if (report.configuration.name == injectInto.name) {
        val moduleReports = report.modules :+ ModuleReport(module, artifacts.toVector, Vector.empty)
        report.withModules(moduleReports)
      } else report
    }
    report.withConfigurations(newConfigurationReports)
  }

  def buildExternalDependencyClassPath: Def.Initialize[Task[Seq[Attributed[File]]]] = Def.task {
    val mainJarClassPath = intellijMainJarsClasspath.value
    val pluginClassPaths = intellijPluginJars.value.flatMap(_._2)
    mainJarClassPath ++ pluginClassPaths
  }

  private def intellijMainJarsClasspath: Def.Initialize[Task[Seq[Attributed[File]]]] = Def.taskDyn {
    val jars = intellijMainJars.value
    val artifact = getIdeaMainJarsArtifact(jars)
    val module = getIntellijSdkModule(productInfo.in(ThisBuild).value.buildNumber)
    doBuildTestExternalDependencyClassPath(jars, artifact, module, Compile)
  }

  def buildTestExternalDependencyClassPath: Def.Initialize[Task[Seq[Attributed[File]]]] = Def.taskDyn {
    val jars = intellijMainJars.value
    val artifact = ideaTestArtifact
    val module = getIntellijSdkTestFrameworkModule(productInfo.in(ThisBuild).value.buildNumber)
    doBuildTestExternalDependencyClassPath(jars, artifact, module, Compile)
  }

  private def doBuildTestExternalDependencyClassPath(
    jars: Classpath,
    artifact: Artifact,
    module: ModuleID,
    configuration: librarymanagement.Configuration
  ): Def.Initialize[Task[Seq[Attributed[File]]]] = Def.task {
    val sdkAttrs: AttributeMap = AttributeMap.empty
      .put(Keys.artifact.key, artifact)
      .put(Keys.moduleID.key, module)
      .put(Keys.configuration.key, configuration)
    jars.map(_.data).map(Attributed(_)(sdkAttrs))
  }
}
