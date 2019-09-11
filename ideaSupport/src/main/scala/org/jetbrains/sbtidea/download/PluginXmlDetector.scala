package org.jetbrains.sbtidea.download

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}
import java.util.Collections
import java.util.function.Predicate

private class PluginXmlDetector extends Predicate[Path] {

  import org.jetbrains.sbtidea.packaging.artifact._

  private val MAP = Collections.emptyMap[String, Any]()
  var result: String = _

  override def test(t: Path): Boolean = {
    if (!t.toString.endsWith(".jar"))
      return false

    val uri = URI.create(s"jar:${t.toUri}")
    using(FileSystems.newFileSystem(uri, MAP)) { fs =>
      val maybePluginXml = fs.getPath("META-INF", "plugin.xml")
      if (Files.exists(maybePluginXml)) {
        result = new String(Files.readAllBytes(maybePluginXml))
        true
      } else { false }
    }
  }
}
