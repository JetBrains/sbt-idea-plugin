package org.jetbrains.sbtidea.download.idea

import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.download.{BuildInfo, FileDownloader, IdeaUpdater, NioUtils}
import org.jetbrains.sbtidea.{PathExt, PluginLogger as log}

import java.io.File
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}

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
    NioUtils.delete(tmpDir)
    Files.createDirectories(tmpDir)

    object Extensions {
      val Zip = ".zip"
      val TarGz = ".tar.gz"
      val Dmg = ".dmg"

      val all: Seq[String] = Seq(Zip, TarGz, Dmg)
    }

    val artifactFileName = artifact.getFileName.toString
    if (artifactFileName.endsWith(Extensions.Zip)) {
      val res = sbt.IO.unzip(artifact.toFile, tmpDir.toFile)
      if (res.isEmpty)
        throw new RuntimeException(s"Failed to unzip ${artifact.toFile} - bad archive")
    } else if (artifactFileName.endsWith(Extensions.TarGz)) {
      val rc = s"tar xfz $artifact -C $tmpDir --strip 1".!
      if (rc != 0)
        throw new RuntimeException(s"Failed to install ${buildInfo.edition.name} dist: tar command failed")
    } else if (artifactFileName.endsWith(Extensions.Dmg)) {
      // dmg will be installed in a single operation
    } else {
      throw new RuntimeException(s"Unexpected dist archive format: $artifactFileName. Supported formats: ${Extensions.all.mkString(",")}")
    }

    if (ctx.baseDirectory.exists) {
      log.warn(s"IJ install directory already exists, removing... (${ctx.baseDirectory})")
      NioUtils.delete(ctx.baseDirectory)
    }

    buildInfo.edition match {
      case MPS if Files.list(tmpDir).count() == 1 => // MPS may add additional folder level to the artifact
        log.info("MPS detected: applying install dir quirks")
        val actualDir = Files.list(tmpDir).iterator().next()
        Files.move(actualDir, ctx.baseDirectory)
        Files.deleteIfExists(tmpDir)
      case _ =>
        if (artifactFileName.endsWith(Extensions.Dmg)) {
          installDmgApp(artifact, ctx.baseDirectory)
        } else {
          Files.move(tmpDir, ctx.baseDirectory)
        }
    }

    fixAccessRights(ctx.baseDirectory)

    if (!keepDownloadedFiles) {
      log.info(s"Deleting $artifact")
      NioUtils.delete(artifact)
    }

    NioUtils.delete(tmpDir)
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
        case e: Exception =>
          log.warn(s"Failed to fix access rights for $binDir: ${e.getMessage}")
      }
    }
  }

  private def installDmgApp(
    pathToDmg: Path,
    installTo: Path
  ): Unit = {
    import sys.process.*

    // Step 1: Mount the dmg file
    Seq("hdiutil", "attach", pathToDmg.toString).!.ensuring(_ == 0, s"Failed to mount dmg file: $pathToDmg")

    val mountPoint = findIntelliJIdeaVolume
    val appName = mountPoint.listFiles.find(_.getName.endsWith(".app")).get.getName

    val installFrom = s"$mountPoint/$appName"
    try {
      // Step 2: Copy the .app file to /Applications
      // Ensure that the target directory is created.
      // Otherwise, "cp" won't copy the app but will copy its contents
      installTo.toFile.mkdirs()
      Seq("cp", "-R", installFrom, installTo.toString).!.ensuring(_ == 0, s"Failed to copy $installFrom to $installTo")
    } finally {
      // Step 3: Unmount the dmg file
      Seq("hdiutil", "detach", mountPoint.toString).!.ensuring(_ == 0, s"Failed to unmount dmg file: $pathToDmg")
    }
  }

  private def findIntelliJIdeaVolume: File = {
    val intellijIdeaVolumes = new File("/Volumes/").listFiles().filter(_.getName.contains("IntelliJ IDEA")).filter(_.listFiles().exists(_.getName.endsWith(".app")))
    val volume = intellijIdeaVolumes match {
      case Array(volume) => volume
      case Array() =>
        throw new RuntimeException("No IntelliJ IDEA app found in /Volumes")
      case multipleVolumes =>
        throw new RuntimeException(s"Multiple IntelliJ IDEA apps found: ${multipleVolumes.mkString(", ")}")
    }
    volume
  }
}