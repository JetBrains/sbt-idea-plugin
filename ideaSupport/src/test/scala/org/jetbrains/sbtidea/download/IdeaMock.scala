package org.jetbrains.sbtidea.download

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipInputStream}

import org.jetbrains.sbtidea.{Keys, TmpDirUtils}
import org.jetbrains.sbtidea.Keys.String2Plugin
import org.jetbrains.sbtidea.packaging.artifact

import scala.collection.JavaConverters.asScalaIteratorConverter

trait IdeaMock extends TmpDirUtils {
  protected val IDEA_VERSION      = "192.5728.12"
  protected val IDEA_EDITION      = "IU"
  protected val IDEA_DIST         = s"idea$IDEA_EDITION-$IDEA_VERSION.zip"
  protected val IDEA_DIST_PATH    = s"/org/jetbrains/sbtidea/download/$IDEA_DIST"
  protected val IDEA_BUILDINFO: BuildInfo = BuildInfo(IDEA_VERSION, Keys.IntelliJPlatform.IdeaUltimate)

  implicit class PathExt(path: Path) {
    def /(string: String): Path = path.resolve(string)
    def list: Seq[Path] = Files.list(path).iterator().asScala.toSeq
  }

  protected val bundledPlugins: List[Keys.IntellijPlugin] =
    "org.jetbrains.plugins.yaml".toPlugin ::
    "com.intellij.properties".toPlugin :: Nil

  protected def installIdeaMock: Path = {
    val tmpDir      = newTmpDir
    val installDir  = Files.createDirectory(tmpDir.resolve(IDEA_VERSION))
    val stream      = getClass.getResourceAsStream(IDEA_DIST_PATH)
    artifact.using(new ZipInputStream(stream)) { zip =>
      var entry: ZipEntry = zip.getNextEntry
      while (entry != null) {
        val toPath = installDir.resolve(entry.getName)
        if (entry.isDirectory)
          Files.createDirectory(toPath)
        else
          Files.copy(zip, toPath)
        entry = zip.getNextEntry
      }
    }
    installDir
  }

  protected def getDistCopy: Path = Files.copy(getIdeaDistMockPath, newTmpDir.resolve(IDEA_DIST))

  protected def getIdeaDistMockURI: URI = getClass.getResource(IDEA_DIST_PATH).toURI

  protected def getIdeaDistMockPath: Path = Paths.get(getIdeaDistMockURI)
}
