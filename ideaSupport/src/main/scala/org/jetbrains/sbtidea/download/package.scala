package org.jetbrains.sbtidea

import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path}
import com.eclipsesource.json.Json
import org.jetbrains.sbtidea.Keys.{IntelliJPlatform => _}

import scala.concurrent.duration.DurationInt

package object download {

  case class BuildInfo(buildNumber: String, edition: IntelliJPlatform) {
    override def toString: String = s"BuildInfo($edition-$buildNumber)"
  }

  object BuildInfo {
    val LATEST_EAP_SNAPSHOT = "LATEST-EAP-SNAPSHOT"
  }

  def withConnection[V](url: URL)(f: => HttpURLConnection => V): V = {
    var connection: HttpURLConnection = null
    try {
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(5.seconds.toMillis.toInt)
      connection.setReadTimeout(30.seconds.toMillis.toInt)
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

    def getDeclaredOrActualNoSnapshotBuild(ideaRoot: Path): String =
      if (buildInfo.buildNumber == BuildInfo.LATEST_EAP_SNAPSHOT)
        getActualIdeaBuild(ideaRoot)
      else
        buildInfo.buildNumber

    def getActualIdeaBuild(ideaRoot: Path): String = {
      val productInfo = ideaRoot.resolve("product-info.json")
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
