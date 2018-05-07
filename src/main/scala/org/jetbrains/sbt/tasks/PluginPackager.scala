package org.jetbrains.sbt
package tasks

import org.jetbrains.sbtidea.Keys.PackagingMethod
import org.jetbrains.sbtidea.Keys.PackagingMethod.{MergeIntoOther, MergeIntoParent, Skip, Standalone}
import sbt.Def.Classpath
import sbt.Keys.moduleID
import sbt.jetbrains.apiAdapter._
import sbt.{ModuleID, ProjectRef, File, _}

object PluginPackager {
  case class ModuleKey(id:ModuleID, attributes: Map[String,String])
  case class ProjectData(thisProject: ProjectRef,
                         cp: Classpath, productDirs: Seq[File],
                         libMapping: Seq[(ModuleID, Option[String])],
                         additionalMappings: Seq[(File, File)],
                         packageMethod: PackagingMethod)

  def apply(rootProject: ProjectRef,
            outputDir: File,
            projectsData: Seq[ProjectData],
            buildDependencies: BuildDependencies): File = {

    def mkProjectData(projectData: ProjectData): ProjectData = {
      if (projectData.thisProject == rootProject && !projectData.packageMethod.isInstanceOf[Standalone]) {
        projectData.copy(packageMethod = Standalone())
      } else projectData
    }

    val projectMap = projectsData.iterator.map(x => x.thisProject -> mkProjectData(x) ).toMap
    val revProjectMap = projectsData.iterator.flatMap(x => buildDependencies.classpathRefs(x.thisProject).map(_ -> x.thisProject)).toMap


    def buildStructure(ref: ProjectRef): Map[File, File] = {
      def findParentToMerge(ref: ProjectRef): ProjectRef = projectMap.getOrElse(ref,
        throw new RuntimeException(s"Project $ref has no parent to merge into")) match {
          case ProjectData(p, _, _, _, _, _: Standalone) => p
          case _ => findParentToMerge(revProjectMap(ref))
      }

      var artifactMap = Map[File, File]()
      val ProjectData(_, cp, productDirs, libMapping, additionalMappings, method) = projectMap(ref)
      val mappings = libMapping.map(m => moduleKey(m._1) -> m._2).toMap
      val resolvedLibs = for {
        jarFile <- cp
        moduleId <- jarFile.get(moduleID.key)
      } yield { (moduleKey(moduleId), jarFile.data)}

      val processedLibs = resolvedLibs.collect {
        case (mod, file) if !mappings.contains(mod) => file -> outputDir / mkRelativeLibPath(file)
        case (mod, file) if mappings(mod).isDefined => file -> outputDir / mappings(mod).get
      }

      method match {
        case Skip() =>
        case MergeIntoParent() =>
          val parent = findParentToMerge(ref)
          val parentFile = mkProjectJarPath(parent)
            productDirs.foreach { artifactMap += _ -> outputDir / parentFile }
        case MergeIntoOther(projectRef) =>
          val otherFile = mkProjectJarPath(projectRef)
          productDirs.foreach { artifactMap += _ -> outputDir/ otherFile }
        case Standalone(targetFile) =>
          val file = targetFile.getOrElse(outputDir / mkProjectJarPath(ref))
          productDirs.foreach { artifactMap += _ -> file }
      }

      artifactMap ++= processedLibs
      artifactMap ++= additionalMappings

      buildDependencies.classpathRefs(ref).map(buildStructure).foreach(artifactMap ++= _)
      artifactMap
    }

    println(buildStructure(rootProject).mkString("\n"))
    outputDir
  }

  /**
    * Extract only key-relevant parts of the ModuleId, so that mappings succeed even if they contain extra attributes
    */
  private def moduleKey(moduleId: ModuleID): ModuleKey =
    ModuleKey (
      moduleId.organization % moduleId.name % moduleId.revision,
      moduleId.extraAttributes
        .map { case (k,v) => k.stripPrefix("e:") -> v}
        .filter { case (k,_) => k == "scalaVersion" || k == "sbtVersion" }
    )

  private def mkProjectJarPath(ref: ProjectRef) = s"${ref.project}.jar"

  private def mkRelativeLibPath(lib: File) = s"lib/${lib.getName}"
}
