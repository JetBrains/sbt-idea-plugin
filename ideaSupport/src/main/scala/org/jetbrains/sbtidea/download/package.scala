package org.jetbrains.sbtidea

import java.net.{HttpURLConnection, URL}

import org.jetbrains.sbtidea.Keys._

package object download {

  case class BuildInfo(buildNumber: String, edition: IntelliJPlatform, jbrVersion: Option[String]) {
    override def toString: String = s"BuildInfo($edition-$buildNumber)"
  }

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
