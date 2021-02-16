package org.jetbrains.sbtidea

import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path}

import com.eclipsesource.json.Json
import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.pathToPathExt
import sbt._

package object download {

  case class BuildInfo(buildNumber: String, edition: IntelliJPlatform) {
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

  implicit class BuildInfoOps(val buildInfo: BuildInfo) {
    def getActualIdeaBuild(ideaRoot: Path): String = {
      val productInfo = ideaRoot / "product-info.json"
      if (buildInfo.buildNumber.count(_ == '.') < 2 && productInfo.exists) { // most likely some LATEST-EAP-SNAPSHOT kind of version
        try {
          val content = new String(Files.readAllBytes(productInfo))
          val parsed = Json.parse(content)
          parsed.asObject().getString("buildNumber", buildInfo.buildNumber)
        } catch {
          case _: Throwable => buildInfo.buildNumber
        }
      } else buildInfo.buildNumber
    }
  }

}
