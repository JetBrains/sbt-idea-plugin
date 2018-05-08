package org.jetbrains.sbt
package tasks

import java.io._
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util
import java.util.zip.{ZipEntry, ZipException, ZipInputStream, ZipOutputStream}

import org.jetbrains.sbtidea.Keys.PackagingMethod
import org.jetbrains.sbtidea.Keys.PackagingMethod.{MergeIntoOther, MergeIntoParent, Skip, Standalone}
import sbt.Def.Classpath
import sbt.Keys.moduleID
import sbt.jetbrains.apiAdapter._
import sbt.{File, ModuleID, ProjectRef, _}

object PluginPackager {
  case class ModuleKey(id:ModuleID, attributes: Map[String,String]){
    override def equals(o: scala.Any): Boolean = o match {
      case ModuleKey(_id, _attributes) =>
        id.organization.equals(_id.organization) &&
          id.name.matches(_id.name) &&
          id.revision.matches(_id.revision) &&
          attributes == _attributes
      case _ => false
    }
    override def hashCode(): Int = id.organization.hashCode
  }
  case class ProjectData(thisProject: ProjectRef,
                         cp: Classpath, productDirs: Seq[File],
                         libMapping: Seq[(ModuleID, Option[String])],
                         additionalMappings: Seq[(File, File)],
                         packageMethod: PackagingMethod)

  def artifactMappings(rootProject: ProjectRef,
            outputDir: File,
            projectsData: Seq[ProjectData],
            buildDependencies: BuildDependencies): Map[File, File] = {

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
      val mappings = new util.HashMap[ModuleKey, Option[String]]()
      libMapping.foreach(m => mappings.put(moduleKey(m._1), m._2))
      val resolvedLibs = for {
        jarFile <- cp
        moduleId <- jarFile.get(moduleID.key)
      } yield { (moduleKey(moduleId), jarFile.data) }

      val processedLibs = resolvedLibs.collect {
        case (mod, file) if !mappings.containsKey(mod)                 => file -> outputDir / mkRelativeLibPath(file)
        case (mod, file) if mappings.getOrDefault(mod, None).isDefined => file -> outputDir / mappings.get(mod).get
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

      buildDependencies.classpathRefs(ref).map(buildStructure).foreach(artifactMap ++= _)

      artifactMap ++= processedLibs
      artifactMap ++= additionalMappings

      artifactMap
    }

    buildStructure(rootProject)
  }

  def packageArtifact(structure: Map[File, File]): Unit = structure.foreach { entry =>
    val (from, to) = entry
    if (from.isDirectory) {
      if (to.isDirectory) IO.copyDirectory(from, to)
      else                zipDirectory(from, to)
    } else {
      if (to.isDirectory) IO.copy(Seq(from -> to))
      else                copyZipToZip(from, to)
    }
  }

  private def copyZipToZip(input: File, output: File): Unit = {
    if (!output.exists()) {
      output.getParentFile.mkdirs()
      Files.copy(Paths.get(input.toURI), Paths.get(output.toURI))
    } else {
      val inStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(input)))
      val outStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))
      try {
        val buffer = new Array[Byte](64 * 1024)
        var entry = inStream.getNextEntry
        while (entry != null) {
          try {
            outStream.putNextEntry(entry)
            var numRead = inStream.read(buffer)
            while (numRead > 0) {
              outStream.write(buffer, 0, numRead)
              numRead = inStream.read(buffer)
            }
            outStream.closeEntry()
          } catch {
            case ze: ZipException if ze.getMessage.startsWith("duplicate entry") => //ignore
            case e: IOException => println(s"$e")
          }
          entry = inStream.getNextEntry
        }
      } finally {
        if (inStream != null) inStream.close()
      }
    }
  }

  private def zipDirectory(dir: File, output: File): Unit = {
    if (!output.exists()) {
      output.getParentFile.mkdirs()
      output.createNewFile()
    }
    val outputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))
    val folder = Paths.get(dir.toURI)

    Files.walkFileTree(folder, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        outputStream.putNextEntry(new ZipEntry(folder.relativize(file).toString))
        Files.copy(file, outputStream)
        outputStream.closeEntry()
        FileVisitResult.CONTINUE
      }
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        outputStream.putNextEntry(new ZipEntry(folder.relativize(dir).toString + "/"))
        outputStream.closeEntry()
        FileVisitResult.CONTINUE
      }
    })
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
