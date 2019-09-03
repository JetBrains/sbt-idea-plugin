package org.jetbrains.sbtidea.download

import java.io.File
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import java.util.function.Consumer

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea.download.api.IdeaInstaller
import sbt._

class CommunityIdeaInstaller(ideaInstallDir: File,
                             override val buildInfo: BuildInfo,
                             override val log: PluginLogger) extends IdeaInstaller with  IdeaPluginInstaller {

  override def getInstallDir: File = ideaInstallDir

  override def isIdeaAlreadyInstalled: Boolean = getInstallDir.exists() && getInstallDir.listFiles().nonEmpty

  protected def tmpDir: File = getInstallDir.getParentFile / s"${buildInfo.edition.name}-${buildInfo.buildNumber}-TMP"

  override def installIdeaDist(files: Seq[(ArtifactPart, File)]): File = {
    val dist = files
      .collectFirst { case (ArtifactPart(_, ArtifactKind.IDEA_DIST, _, _), file) => file }
      .getOrElse(throw new RuntimeException(s"Can't install IDEA: distribution is missing: $files"))
    val src = files
      .collectFirst { case (ArtifactPart(_, ArtifactKind.IDEA_SRC, _, _), file) if file.exists() => file }
    val extras = files
      .collect({ case a@(ArtifactPart(_, ArtifactKind.MISC, _, _), _) => a })

    ensureFolderStructure()

    installDist(dist)
    installExtras(extras)
    if (src.nonEmpty)
      installSources(src.head)
    else log.warn(s"No IDEA sources have been downloaded")

    fixAccessRights()

    getInstallDir
  }

  protected def installDist(file: File): Unit = {
    import sys.process._

    log.info(s"Extracting IDEA dist to $tmpDir")

    if (file.name.endsWith(".zip")) {
      sbt.IO.unzip(file, tmpDir)
    } else if (file.name.endsWith(".tar.gz")) {
      if (s"tar xfz $file -C $tmpDir --strip 1".! != 0) {
        throw new RuntimeException(s"Failed to install IDEA dist: tar command failed")
      }
    } else throw new RuntimeException(s"Unexpected dist archive format(not zip or gzip): $file")

    sbt.IO.move(tmpDir, getInstallDir)
    sbt.IO.delete(file)
    log.info(s"Installed IDEA($buildInfo) to $getInstallDir")
  }

  protected def installSources(file: File): Unit = {
    sbt.IO.move(file, getInstallDir / "sources.zip")
    log.info(s"IDEA sources installed")
  }

  protected def installExtras(files: Seq[(ArtifactPart, File)]): Unit = ()

  private def fixAccessRights(): Unit = {
    if (!System.getProperty("os.name").startsWith("Windows")) {
      val execPerms = PosixFilePermissions.fromString("rwxrwxr-x")
      val binDir    = getInstallDir / "bin"
      try {
        Files
          .walk(binDir.toPath)
          .forEach(new Consumer[Path] {
            override def accept(t: Path): Unit =
              Files.setPosixFilePermissions(t, execPerms)
          })
      } catch {
        case e: Exception => log.warn(s"Failed to fix access rights for $binDir: ${e.getMessage}")
      }
    }
  }

  private def ensureFolderStructure(): Unit = {
    getInstallDir.getParentFile.mkdirs() // ensure "sdk" directory exists
    sbt.IO.delete(getInstallDir)
    sbt.IO.delete(tmpDir)
    tmpDir.mkdirs()
  }

}
