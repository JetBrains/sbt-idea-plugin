package org.jetbrains.sbtidea.structure.sbtImpl

import java.io.File

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.packaging.ProjectScalaVersion
import org.jetbrains.sbtidea.structure.{Library, ModuleKey}
import sbt.Def.Classpath
import sbt.Keys.moduleID

class IvyLibraryExtractor(private val data: CommonSbtProjectData)
                        (private implicit val scalaVersion: ProjectScalaVersion, log: PluginLogger) {

  private val configuration = "compile"

  private case class SbtIvyLibrary(override val key: ModuleKey,
                                   override val jarFile: File) extends Library

  def extract: Seq[Library] = {
    val resolver = new TransitiveDeps(data.report, configuration)
    val resolvedLibsNoEvicted = buildModuleIdMap(data.cp)
    val resolvedLibs          = updateWithEvictionMappings(resolvedLibsNoEvicted, resolver.evicted)
    val transitiveDeps        = data.definedDeps
      .filter(_.configurations.isEmpty)
      .map(_.key)
      .flatMap(resolver.collectTransitiveDeps)
    val libraries = for {
      dep <- transitiveDeps
      file <- resolvedLibs.get(dep)
    } yield SbtIvyLibrary(dep, file)
    libraries.distinct
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
