package org.jetbrains.sbtidea.download

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

package object plugin {

  private[plugin] def listDirectoryEntries(directory: Path, glob: String = "*"): Seq[Path] =
    if (Files.isDirectory(directory)) {
      Using.resource(Files.newDirectoryStream(directory, glob))(_.iterator().asScala.toList)
    } else {
      Seq.empty
    }
}
