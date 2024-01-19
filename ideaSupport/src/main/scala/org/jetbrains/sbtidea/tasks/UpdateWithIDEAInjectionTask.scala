package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.{intellijAttachSources, intellijBaseDirectory, intellijBuild, intellijMainJars, intellijPluginJars}
import org.jetbrains.sbtidea.download.idea.IdeaSourcesImpl
import sbt.Keys.*
import sbt.{Def, *}

import scala.collection.mutable

object UpdateWithIDEAInjectionTask extends SbtIdeaTask[UpdateReport] {

  private val intellijMainJarsHashToArtifactSuffix = new mutable.ListMap[Int, String]

  private def getIdeaJarsArtifact(intellijMainJarsValue: Classpath): Artifact = intellijMainJarsHashToArtifactSuffix.synchronized {
    val hashCode = intellijMainJarsValue.hashCode() //includes hash codes of all files

    //Sometimes different modules might contain different set of intellijMainJars
    //(it better shouldn't be so, but it might occur accidentally, e.g. due to misconfiguration)
    //In this case we want to create different libraries.
    //For this we need unique names
    if (!intellijMainJarsHashToArtifactSuffix.contains(hashCode)) {
      val alreadyExistingArtifactsWithSameName = intellijMainJarsHashToArtifactSuffix.size
      val newSuffix =
        if (alreadyExistingArtifactsWithSameName == 0) ""
        else s"-$alreadyExistingArtifactsWithSameName"
      intellijMainJarsHashToArtifactSuffix.put(hashCode, newSuffix)
    }

    val artifactSuffix = intellijMainJarsHashToArtifactSuffix(hashCode)
    Artifact("INTELLIJ-SDK", s"IJ-SDK$artifactSuffix")
  }

  private val ideaSourcesArtifact: Artifact =
    Artifact("INTELLIJ-SDK", Artifact.SourceType, "zip", s"IJ-SDK")

  private def buildIdeaModule(build: String): ModuleID =
    "org.jetbrains" % "INTELLIJ-SDK" % build withSources()

  override def createTask: Def.Initialize[Task[sbt.UpdateReport]] = Def.task {
    val intellijMainJarsValue = intellijMainJars.value

    val targetConfiguration     = Configurations.Compile
    val attachSources           = intellijAttachSources.in(Global).value
    val ijBuild                 = intellijBuild.in(ThisBuild).value
    val ideaModule              = buildIdeaModule(ijBuild)
    val originalReport          = update.value
    val intelliJSourcesArchive  = intellijBaseDirectory.value / IdeaSourcesImpl.SOURCES_ZIP
    val ideaArtifacts           =
      intellijMainJarsValue.map(getIdeaJarsArtifact(intellijMainJarsValue) -> _.data) ++
        (if (attachSources)  Seq(ideaSourcesArtifact -> intelliJSourcesArchive)
        else                Seq.empty)
    val reportWithIdeaMainJars  = injectIntoUpdateReport(originalReport, targetConfiguration, ideaArtifacts, ideaModule)

    val pluginClassPaths = intellijPluginJars.value
    pluginClassPaths.foldLeft(reportWithIdeaMainJars) { case (report, (_, classpath)) =>
      classpath.headOption.map { f =>
        val pluginModule = f.get(moduleID.key).get
        val pluginArtifact = f.get(artifact.key).get
        if (attachSources && pluginModule.revision == ijBuild) { // bundled plugin has the same version as platform, add sources
          val pluginArtifacts = classpath.map(pluginArtifact -> _.data) :+
            Artifact(pluginArtifact.name, Artifact.SourceType, "zip", Artifact.SourceClassifier) -> intelliJSourcesArchive
          injectIntoUpdateReport(report, targetConfiguration, pluginArtifacts, pluginModule)
        } else {
          val pluginArtifacts = classpath.map(pluginArtifact -> _.data)
          injectIntoUpdateReport(report, targetConfiguration, pluginArtifacts, pluginModule)
        }
      }.getOrElse(report)
    }
  }

  private def injectIntoUpdateReport(report: UpdateReport, injectInto: Configuration, artifacts: Seq[(Artifact, File)], module: ModuleID): UpdateReport = {
    val newConfigurationReports = report.configurations.map { report =>
      if (report.configuration.name == injectInto.name) {
        val moduleReports = report.modules :+ ModuleReport(module, artifacts.toVector, Vector.empty)
        report.withModules(moduleReports)
      } else report
    }
    report.withConfigurations(newConfigurationReports)
  }

  def buildExternalDependencyClassPath: Def.Initialize[Task[Seq[Attributed[File]]]] = Def.task {
    val intellijMainJarsValue = intellijMainJars.value

    val ideaModule: ModuleID = buildIdeaModule(intellijBuild.in(ThisBuild).value)
    val sdkAttrs: AttributeMap = AttributeMap.empty
      .put(artifact.key, getIdeaJarsArtifact(intellijMainJarsValue))
      .put(moduleID.key, ideaModule)
      .put(configuration.key, Compile)

    val pluginClassPaths = intellijPluginJars.value.flatMap(_._2)

    intellijMainJarsValue.map(_.data).map { file => Attributed(file)(sdkAttrs)} ++ pluginClassPaths
  }
}
