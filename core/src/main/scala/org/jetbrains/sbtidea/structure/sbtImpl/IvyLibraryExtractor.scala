package org.jetbrains.sbtidea.structure.sbtImpl

import java.io.File

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.structure.{Library, ModuleKey}
import sbt.Def.Classpath
import sbt.Keys.moduleID

import scala.collection.mutable

class IvyLibraryExtractor(private val data: CommonSbtProjectData)
                        (private implicit val scalaVersion: ProjectScalaVersion, log: PluginLogger) {

  private val configuration = "compile"

  def extract: Seq[Library] = {
    val resolver = new TransitiveDeps(data.report, configuration)
    val resolvedLibsNoEvicted = buildModuleIdMap(data.cp)
    val resolvedLibs          = updateWithEvictionMappings(resolvedLibsNoEvicted, resolver.evicted)
    val definedDeps           = data.definedDeps
      .filter(_.configurations.isEmpty)
      .map(_.key)
    val transitiveDeps        = definedDeps
      .flatMap(resolver.collectTransitiveDeps)

    if (transitiveDeps.size < definedDeps.size) {
      val diff = definedDeps.toSet.diff(transitiveDeps.toSet)
      log.warn(s"Failed to collect transitive dependencies for $diff")
    }

    val resolved    = mutable.ArrayBuffer[Library]()
    val unResolved  = mutable.ArrayBuffer[ModuleKey]()

    transitiveDeps.foreach { dep =>
      if (resolvedLibs.contains(dep))
        resolved   += SbtIvyLibrary(dep, resolvedLibs(dep))
      else
        unResolved += dep
    }

    unResolved.foreach(d => log.warn(s"Failed to extract dependency jar for $d"))

    resolved.distinct
  }

  private def buildModuleIdMap(cp: Classpath)(implicit scalaVersion: ProjectScalaVersion): Map[ModuleKey, File] = (for {
    jarFile <- cp
    moduleId <- jarFile.get(moduleID.key)
  } yield { moduleId.key -> jarFile.data }).toMap

  private def updateWithEvictionMappings(cpNoEvicted: Map[ModuleKey, File], evicted: Seq[ModuleKey]): Map[ModuleKey, File] = {
    val evictionSubstitutes = evicted
      .map(ev => ev -> cpNoEvicted.find(entry => entry._1 ~== ev).map(_._2)
        .getOrElse(throw new RuntimeException(s"Can't resolve eviction for $ev")))
    cpNoEvicted ++ evictionSubstitutes
  }

}
