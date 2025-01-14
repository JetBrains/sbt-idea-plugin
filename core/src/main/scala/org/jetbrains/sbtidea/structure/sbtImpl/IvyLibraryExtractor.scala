package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.structure.{Library, ModuleKey, ProjectScalaVersion}
import sbt.Def.Classpath
import sbt.librarymanagement.Configurations

import java.io.File
import scala.collection.mutable

class IvyLibraryExtractor(private val data: CommonSbtProjectData)
                        (private implicit val scalaVersion: ProjectScalaVersion, log: PluginLogger) {

  private val configuration = "compile"

  def extract: Seq[Library] = {
    val resolver = new TransitiveDeps(data.report, configuration)

    val resolvedLibsNoEvicted: Map[ModuleKey, Seq[File]] =
      buildModuleIdMap(data.cp)
    val resolvedLibs: Map[ModuleKey, Seq[File]] =
      resolvedLibsNoEvicted ++ evictionMappings(resolvedLibsNoEvicted, resolver.evicted)

    val dependencies: Seq[ModuleKey] =
      getDefinedRuntimeDependenciesWithTransitive(resolver)

    val resolved    = mutable.ArrayBuffer[Library]()
    val unResolved  = mutable.ArrayBuffer[ModuleKey]()
    dependencies.foreach { dep =>
      resolvedLibs.get(dep) match {
        case Some(jarFiles) =>
          resolved += SbtIvyLibrary(dep, jarFiles)
        case None =>
          unResolved += dep
      }
    }

    unResolved.foreach(d => log.warn(s"Failed to extract dependency jar for $d"))

    resolved.distinct
  }

  private def getDefinedRuntimeDependenciesWithTransitive(resolver: TransitiveDeps): Seq[ModuleKey] = {
    val definedDeps: Seq[ModuleKey] = data.definedDeps
      //at runtime there will dependencies with the default configuration (empty) and "runtime" configuration
      .filter(d => d.configurations.isEmpty || d.configurations.contains(Configurations.Runtime.name))
      .map(_.key)
    definedDeps.flatMap(resolver.collectTransitiveDeps)
  }

  private def buildModuleIdMap(cp: Classpath)(implicit scalaVersion: ProjectScalaVersion): Map[ModuleKey, Seq[File]] = {
    val tuples = for {
      cpEntry <- cp
      moduleId <- cpEntry.get(sbt.Keys.moduleID.key)
    } yield moduleId.key -> cpEntry.data
    // The same module id can correspond to multiple artifacts.
    // For example, one jar might be the "main" jar and another can be OS-specific jar.
    // (example: https://github.com/JetBrains/sbt-idea-plugin/issues/135)
    tuples.groupBy(_._1).mapValues(_.map(_._2).toSeq)
  }

  private def evictionMappings(
    cpNoEvicted: Map[ModuleKey, Seq[File]],
    evicted: Seq[ModuleKey]
  ): Map[ModuleKey, Seq[File]] = {
    // ATTENTION:
    // This code might not work correctly when a single artifact was evicted for a given module id.
    // But unfortunately, I can't provide an example to reproduce it.
    evicted.map { ev =>
      val evResolved = cpNoEvicted.find(entry => entry._1 ~== ev).map(_._2)
        .getOrElse(throw new RuntimeException(s"Can't resolve eviction for $ev"))
      ev -> evResolved
    }.toMap
  }
}
