package org.jetbrains.sbtidea.download.jbr

import org.jetbrains.sbtidea.download.api.*
import org.jetbrains.sbtidea.download.{FileDownloader, NioUtils}
import org.jetbrains.sbtidea.{PluginLogger as log, *}
import org.rauschig.jarchivelib.{ArchiveFormat, ArchiverFactory, CompressionType}
import sbt.*

import java.nio.file.{Files, Path}
import java.util.{Locale, Properties}
import scala.util.Using

class JbrInstaller extends Installer[JbrArtifact] {
  import JbrInstaller.*

  override def isInstalled(art: JbrArtifact)(implicit ctx: IdeInstallationContext): Boolean =
    (ctx.baseDirectory / JBR_DIR_NAME).exists && isSameJbr(art)

  override def downloadAndInstall(art: JbrArtifact)(implicit ctx: IdeInstallationProcessContext): Unit = {
    val file = FileDownloader(ctx).download(art.dlUrl)
    install(file)
  }

  private def isSameJbr(art: JbrArtifact)(implicit ctx: IdeInstallationContext): Boolean = {
    val releaseFile = getJbrHome(ctx.baseDirectory) / "release"
    val props = new Properties()
    try {
      val jbrInfo = art.caller.jbrInfo
      Using.resource(releaseFile.inputStream)(props.load)
      props
        .getProperty("IMPLEMENTOR_VERSION")
        .lift2Option
        .exists(value => {
          val sameJbr =
            value.contains(jbrInfo.version.major.replace('_', '.')) && // release file has dot major version separators
              value.contains(jbrInfo.version.minor) &&
              value.contains(jbrInfo.kind.value.replace("jbr_", "")) // release file has no "jbr_" prefix
          if (!sameJbr) log.info(s"New JBR is different from installed: $jbrInfo != $value")
          sameJbr
        })
    } catch {
      case ex: Exception =>
        log.warn(s"Failed to check locally installed JBR version(assuming true): $ex")
        true
    }
  }

  private[jbr] def install(dist: Path)(implicit ctx: IdeInstallationProcessContext): Unit = {
    val archiver = ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
    val tmpDir = Files.createTempDirectory(ctx.baseDirectory, "jbr-extract")
    log.info(s"extracting jbr to $tmpDir")
    archiver.extract(dist.toFile, tmpDir.toFile)
    val installPath = ctx.baseDirectory / JBR_DIR_NAME
    val children = tmpDir.list
    if (children.size == 1) {
      NioUtils.delete(installPath)
      Files.move(children.head, installPath)
      NioUtils.delete(tmpDir)
      log.info(s"installed JBR into $installPath")
    } else {
      log.error(s"Unexpected JBR archive structure, expected single directory")
    }
  }
}

object JbrInstaller {
  val JBR_DIR_NAME = "jbr"

  def getJbrHome(intellijBaseDirectory: Path): Path = {
    val isMacOS = sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("mac")
    if (isMacOS)
      intellijBaseDirectory / JbrInstaller.JBR_DIR_NAME / "Contents" / "Home"
    else
      intellijBaseDirectory / JbrInstaller.JBR_DIR_NAME
  }
}