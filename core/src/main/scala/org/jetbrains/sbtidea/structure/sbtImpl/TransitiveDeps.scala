package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.structure.{ModuleKey, ProjectScalaVersion}
import sbt.UpdateReport

//noinspection MapGetOrElseBoolean : scala 2.10 has no Option.contains
class TransitiveDeps(report: UpdateReport, configuration: String)(implicit scalaVersion: ProjectScalaVersion) {
    val structure: Map[ModuleKey, Seq[ModuleKey]] = buildTransitiveStructure()
    val evicted:   Seq[ModuleKey]                 = report.configurations
      .find(_.configuration.toString().contains(configuration))
      .map (_.details.flatMap(_.modules)
          .filter(m => m.evicted && m.evictedReason.map(_ == "latest-revision").getOrElse(false))
        .map(_.module.key)
      ).getOrElse(Seq.empty)

    private def buildTransitiveStructure(): Map[ModuleKey, Seq[ModuleKey]] = {
      report.configurations.find(_.configuration.toString().contains(configuration)) match {
        case Some(conf) =>
          val edges = conf.modules.flatMap(m => m.callers.map(caller => caller.caller.key -> m.module.key))
          edges.foldLeft(Map[ModuleKey, Seq[ModuleKey]]()) {
            case (map, (caller, mod)) if caller.name.startsWith("temp-resolve") => map + (mod -> Seq.empty) // top level dependency
            case (map, (caller, mod)) => map + (caller -> (map.getOrElse(caller, Seq()) :+ mod))
          }
        case None => Map.empty
      }
    }

    def collectTransitiveDeps(moduleID: ModuleKey): Set[ModuleKey] = {
      val deps = structure.getOrElse(moduleID, Seq.empty)
      val depsWithTransitive = deps ++ deps.flatMap(collectTransitiveDeps)
      (depsWithTransitive :+ moduleID).toSet
    }
  }
