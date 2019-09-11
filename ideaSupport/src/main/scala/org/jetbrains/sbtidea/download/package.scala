package org.jetbrains.sbtidea

import java.net.{HttpURLConnection, URL}

import org.jetbrains.sbtidea.Keys._

package object download {

  object ArtifactKind extends Enumeration {
    type ArtifactKind = Value
    val IDEA_DIST, IDEA_SRC, IDEA_PLUGIN, MISC = Value
  }

  case class BuildInfo(buildNumber: String, edition: IdeaEdition)

  case class ArtifactPart(url: URL,
                          kind: ArtifactKind.ArtifactKind,
                          nameHint: String = "",
                          optional: Boolean = false)

  def withConnection[V](url: URL)(f: => HttpURLConnection => V): V = {
    var connection: HttpURLConnection = null
    try {
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      f(connection)
    } finally {
      try {
        if (connection != null) connection.disconnect()
      } catch {
        case e: Exception =>
          println(s"Failed to close connection $url: ${e.getMessage}")
      }
    }
  }

}
