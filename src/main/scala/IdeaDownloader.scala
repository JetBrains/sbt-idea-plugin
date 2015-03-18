package ideaplugin

import sbt._
import sbt.Keys._

import java.net.URL
import java.io.File

import scala.util._
import scala.xml._


object IdeaDownloader {
  final case class Download(from: URL, to: File, unpackTo: Option[File])

  val TeamcityEndpoint = "https://teamcity.jetbrains.com/guestAuth/app/rest"

  def getBuildId(build: String): String = {
    val metadataUrl = url(s"$TeamcityEndpoint/builds?locator=buildType:(id:bt410),branch:(default:any,name:idea/$build)")

    val buildId = for {
      metadata <- Try(XML.loadString(IO.readLinesURL(metadataUrl).mkString))
      buildVal <- Try(metadata \ "build" \\ "@id").map(_.head)
    } yield buildVal.text

    buildId.recoverWith { case exc =>
      Failure(new Error(s"Could not retrieve IDEA/$build build ID from $metadataUrl", exc))
    }.get
  }

  def downloadArtifact(d: Download, streams: TaskStreams): Unit = d match {
    case Download(from, to, unpackToOpt) =>
      if (to.isFile) {
        streams.log.info(s"$to exists, download aborted")
      } else {
        streams.log.info(s"Downloading $from to $to")
        IO.download(from, to)
      }
      unpackToOpt.foreach { unpackTo =>
        streams.log.info(s"Unpacking $to to $unpackTo")
        IO.unzip(to, unpackTo)
      }
  }
}
