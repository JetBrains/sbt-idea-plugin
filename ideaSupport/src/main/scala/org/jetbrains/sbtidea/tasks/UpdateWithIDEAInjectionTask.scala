package org.jetbrains.sbtidea.tasks

import org.apache.commons.io.FileUtils
import org.jetbrains.sbtidea.Keys.{intellijAttachSources, intellijBaseDirectory, intellijMainJars, intellijPluginJarsClasspath, intellijTestJars, productInfo}
import org.jetbrains.sbtidea.download.idea.IdeaSourcesInstaller
import org.jetbrains.sbtidea.download.plugin.PluginDescriptor
import org.jetbrains.sbtidea.tasks.classpath.AttributedClasspathTasks.{Artifacts, Modules}
import sbt.*
import sbt.Keys.*

import scala.collection.Seq
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

object UpdateWithIDEAInjectionTask extends SbtIdeaTask[UpdateReport] {

  override def createTask: Def.Initialize[Task[sbt.UpdateReport]] = Def.task {
    val updateReportOld = update.value
    val intellijBaseDir = intellijBaseDirectory.value

    val intellijMainJarsValue = intellijMainJars.value
    val intellijTestJarsValue = intellijTestJars.value
    val intellijPluginJarsClasspathValue = intellijPluginJarsClasspath.value

    val attachSources = intellijAttachSources.in(Global).value
    val buildNumber = productInfo.in(ThisBuild).value.buildNumber

    injectModules(
      updateReportOld,
      intellijBaseDir,
      intellijMainJarsValue,
      intellijTestJarsValue,
      intellijPluginJarsClasspathValue,
      buildNumber,
      attachSources,
    )
  }

  private def injectModules(
    reportOriginal: UpdateReport,
    intellijBaseDir: File,
    intellijMainJars: Seq[File],
    intellijTestJars: Seq[File],
    intellijPluginClasspath: Seq[(PluginDescriptor, Classpath)],
    buildNumber: String,
    attachSources: Boolean,
  ): UpdateReport = {
    val ideaModule: ModuleID = Modules.getIntellijSdkModule(buildNumber)
    val ideaModuleTest: ModuleID = Modules.getIntellijSdkTestModule(buildNumber)

    def findSourcesArchives(intellijBaseDir: File): Seq[File] = {
      val sourcesDir = IdeaSourcesInstaller.sourcesRoot(intellijBaseDir.toPath).toFile
      if (sourcesDir.isDirectory) FileUtils.listFiles(sourcesDir, Array("zip", "jar"), false).asScala.toSeq
      else Seq()
    }

    val intelliJSourcesArchives = findSourcesArchives(intellijBaseDir)

    val ideaArtifactsMapping: Seq[(sbt.Artifact, File)] = {
      val mainJarsArtifact: Artifact = Artifacts.ideaMainJarsArtifact(intellijMainJars)
      val sourcesArtifactMapping = if (attachSources) intelliJSourcesArchives.map(Artifacts.ideaSourcesArtifact -> _) else Seq.empty
      intellijMainJars.map(mainJarsArtifact -> _) ++ sourcesArtifactMapping
    }

    val ideaTestArtifactMappings: Seq[(sbt.Artifact, File)] = {
      //testFramework sources are located in the same archive as main sources
      val sourcesArtifactMapping = if (attachSources) intelliJSourcesArchives.map(Artifacts.ideaTestSourcesArtifact -> _) else Seq.empty
      val testJarsArtifact = Artifacts.ideaTestArtifact
      intellijTestJars.map(testJarsArtifact -> _) ++ sourcesArtifactMapping
    }

    val pluginArtifactsMappings: Seq[(ModuleID, Seq[(Artifact, File)], Configuration)] = intellijPluginClasspath.flatMap { case (_, classpath) =>
      val representativeJar = classpath.headOption
      representativeJar.map { f =>
        //NOTE: in current architecture plugins are attributed, but intellij jars are not?
        // It's not consistent, and it's better to unify it in one place
        val pluginModule = f.get(moduleID.key).get
        val pluginArtifact = f.get(artifact.key).get
        val mainArtifactMapping = classpath.map(pluginArtifact -> _.data)
        //bundled plugin sources are located in the same archive as main sources
        val sourcesArtifactMappings = intelliJSourcesArchives.map(Artifacts.pluginSourcesArtifact(pluginArtifact.name) -> _)

        // bundled plugin has the same version as the platform; add sources
        val mappings = if (attachSources && pluginModule.revision == buildNumber)
          mainArtifactMapping ++ sourcesArtifactMappings
        else
          mainArtifactMapping
        (pluginModule, mappings, Configurations.Compile)
      }
    }

    val injectInfos: Seq[(sbt.ModuleID, Seq[(sbt.Artifact, sbt.File)], Configuration)] = Seq(
      (ideaModule, ideaArtifactsMapping, Configurations.Compile),
      (ideaModuleTest, ideaTestArtifactMappings, Configurations.Test),
    ) ++ pluginArtifactsMappings

    val reportNew = injectInfos.foldLeft(reportOriginal) { case (previousReport, (module, artifactMappings, configuration)) =>
      injectModulesIntoUpdateReport(previousReport, module, artifactMappings, configuration)
    }
    reportNew //keep a separate variable for easier debugging
  }

  private def injectModulesIntoUpdateReport(
    report: UpdateReport,
    module: ModuleID,
    artifacts: Seq[(Artifact, File)],
    configuration: Configuration,
  ): UpdateReport = {
    val newConfigurationReports = report.configurations.map { confReport =>
      if (confReport.configuration.name == configuration.name) {
        val newModuleReport = ModuleReport(module, artifacts.toVector, Vector.empty)
        confReport.withModules(confReport.modules :+ newModuleReport)
      } else
        confReport
    }
    report.withConfigurations(newConfigurationReports)
  }
}
