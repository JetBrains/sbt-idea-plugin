package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.download.{BuildInfo, FileDownloader, IdeaUpdater, NioUtils}
import org.jetbrains.sbtidea.{PathExt, PluginLogger as log}

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import java.util.function.Consumer

class IdeaDistInstaller(buildInfo: BuildInfo) extends Installer[IdeaDist] {

  override def isInstalled(art: IdeaDist)(implicit ctx: InstallContext): Boolean =
    IdeaUpdater.isDumbIdea ||
      ctx.baseDirectory.toFile.exists() &&
       ctx.baseDirectory.toFile.listFiles().nonEmpty

  override def downloadAndInstall(art: IdeaDist)(implicit ctx: InstallContext): Unit = {
    val artifactPath = FileDownloader(ctx.baseDirectory.getParent).download(art.dlUrl)
    installDist(artifactPath)
  }

  private def tmpDir(implicit ctx: InstallContext): Path =
    ctx.baseDirectory.getParent.resolve(s"${buildInfo.edition.name}-${buildInfo.buildNumber}-TMP")

  private[idea] def installDist(artifact: Path)(implicit ctx: InstallContext): Path = {
    import org.jetbrains.sbtidea.Keys.IntelliJPlatform.MPS

    import sys.process.*

    log.info(s"Extracting ${buildInfo.edition.name} dist to $tmpDir")

    ctx.baseDirectory.toFile.getParentFile.mkdirs() // ensure "sdk" directory exists
    NioUtils.delete(ctx.baseDirectory)
    NioUtils.delete(tmpDir)
    Files.createDirectories(tmpDir)

    if (artifact.getFileName.toString.endsWith(".zip")) {
      val res = sbt.IO.unzip(artifact.toFile, tmpDir.toFile)
      if (res.isEmpty)
        throw new RuntimeException(s"Failed to unzip ${artifact.toFile} - bad archive")
    } else if (artifact.getFileName.toString.endsWith(".tar.gz")) {
      if (s"tar xfz $artifact -C $tmpDir --strip 1".! != 0) {
        throw new RuntimeException(s"Failed to install ${buildInfo.edition.name} dist: tar command failed")
      }
    } else throw new RuntimeException(s"Unexpected dist archive format(not zip or gzip): $artifact")

    if (ctx.baseDirectory.exists) {
      log.warn("IJ install directory already exists, removing...")
      NioUtils.delete(ctx.baseDirectory)
    }

    buildInfo.edition match {
      case MPS if Files.list(tmpDir).count() == 1 => // MPS may add additional folder level to the artifact
        log.info("MPS detected: applying install dir quirks")
        val actualDir = Files.list(tmpDir).iterator().next()
        Files.move(actualDir, ctx.baseDirectory)
        Files.deleteIfExists(tmpDir)
      case _ =>
        Files.move(tmpDir, ctx.baseDirectory)
    }

    fixAccessRights(ctx.baseDirectory)

    if (!keepDownloadedFiles) {
      NioUtils.delete(artifact)
    }

    log.info(s"Installed ${buildInfo.edition.name}($buildInfo) to ${ctx.baseDirectory}")
    ctx.baseDirectory
  }

    private def fixAccessRights(ideaDir: Path): Unit = {
      if (!System.getProperty("os.name").startsWith("Windows")) {
        val execPerms = PosixFilePermissions.fromString("rwxrwxr-x")
        val binDir    = ideaDir.resolve("bin")
        try {
          Files
            .walk(binDir)
            .forEach((t: Path) => Files.setPosixFilePermissions(t, execPerms))
        } catch {
          case e: Exception => log.warn(s"Failed to fix access rights for $binDir: ${e.getMessage}")
        }
      }
    }

}