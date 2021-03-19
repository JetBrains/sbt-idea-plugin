package org.jetbrains.sbtidea.tasks

import org.jetbrains.sbtidea.Keys.{intellijAttachSources, intellijBaseDirectory, intellijBuild, intellijMainJars, intellijPluginJars}
import org.jetbrains.sbtidea.download.idea.IdeaSourcesImpl
import sbt.Keys.{artifact, configuration, moduleID, update}
import sbt._

object UpdateWithIDEAInjectionTask extends SbtIdeaTask[UpdateReport] {

  private val ideaJarsArtifact    = Artifact("INTELLIJ-SDK", "IJ-SDK")
  private val ideaSourcesArtifact = Artifact("INTELLIJ-SDK", Artifact.SourceType, "zip", "IJ-SDK")
  private def buildIdeaModule(build: String): ModuleID =
    "org.jetbrains" % "INTELLIJ-SDK" % build withSources()

  override def createTask = Def.task {
    import org.jetbrains.sbtidea.ApiAdapter._
    val targetConfiguration     = Configurations.Compile
    val attachSources           = intellijAttachSources.in(Global).value
    val ijBuild                 = intellijBuild.in(ThisBuild).value
    val ideaModule              = buildIdeaModule(ijBuild)
    val originalReport          = update.value
    val intelliJSourcesArchive  = intellijBaseDirectory.value / IdeaSourcesImpl.SOURCES_ZIP
    val ideaArtifacts           =
      intellijMainJars.value.map(ideaJarsArtifact -> _.data) ++
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
            (Artifact(pluginArtifact.name, Artifact.SourceType, "zip", Artifact.SourceClassifier) -> intelliJSourcesArchive)
          injectIntoUpdateReport(report, targetConfiguration, pluginArtifacts, pluginModule)
        } else {
          val pluginArtifacts = classpath.map(pluginArtifact -> _.data)
          injectIntoUpdateReport(report, targetConfiguration, pluginArtifacts, pluginModule)
        }
      }.getOrElse(report)
    }
  }

  def buildExternalDependencyClassPath = Def.task {
    val ideaModule: ModuleID = buildIdeaModule(intellijBuild.in(ThisBuild).value)
    val sdkAttrs: AttributeMap = AttributeMap.empty
      .put(artifact.key, ideaJarsArtifact)
      .put(moduleID.key, ideaModule)
      .put(configuration.key, Compile)

    val pluginClassPaths = intellijPluginJars.value.flatMap(_._2)

    intellijMainJars.value.map(_.data).map{ i=>Attributed(i)(sdkAttrs)} ++ pluginClassPaths
  }
}
