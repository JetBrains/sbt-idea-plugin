package org.jetbrains.sbtidea

import com.eclipsesource.json.Json
import org.jetbrains.sbtidea.Keys.IntelliJPlatform as _
import org.jetbrains.sbtidea.PluginLogger as log
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt

package object download {

  case class BuildInfo(buildNumber: String, edition: IntelliJPlatform) {
    override def toString: String = s"BuildInfo($edition-$buildNumber)"
  }

  object BuildInfo {
    val LATEST_EAP_SNAPSHOT = "LATEST-EAP-SNAPSHOT"

    val EAP_CANDIDATE_SNAPSHOT_SUFFIX = "-EAP-CANDIDATE-SNAPSHOT"
    val EAP_SNAPSHOT_SUFFIX = "-EAP-SNAPSHOT"
    val SNAPSHOT_SUFFIX = "-SNAPSHOT"
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

  implicit class BuildInfoOps(private val buildInfo: BuildInfo) extends AnyVal {

    def getActualIdeaBuild(ideaRoot: Path): String = {
      //Just for the record: build id is already present in `build.txt` file
      val productInfo = ideaRoot.resolve("product-info.json")
      val actualBuild = if (productInfo.exists)
        try {
          val content = new String(Files.readAllBytes(productInfo))
          val parsed = Json.parse(content)
          val buildNumberValue = Option(parsed.asObject().getString("buildNumber", null))
          buildNumberValue.toRight(s"Can't find `buildNumber` key in product info file: $productInfo")
        } catch {
          case t: Throwable =>
            Left(s"Error reading `buildNumber` from product-info file $productInfo: ${t.getMessage}")
        }
      else
        Left(s"Can't resolve product-info file: $productInfo")

      actualBuild match {
        case Left(errorMessage) =>
          val fallbackValue = buildInfo.buildNumber
          log.error(s"[getActualIdeaBuild] $errorMessage")
          log.error(s"[getActualIdeaBuild] Fallback to build number: $fallbackValue")
          fallbackValue
        case Right(value) =>
          value
      }
    }

  }

  val NotFoundHttpResponseCode = 404
}
