package ideaplugin

import sbt._
import sbt.Keys._
import scala.util._
import scala.xml._
import scala.language.postfixOps

object Tasks {
  def updateIdea(baseDir: File, build: String, streams: TaskStreams): Unit = {

    val tcEndpoint = "https://teamcity.jetbrains.com/guestAuth/app/rest"

    val buildId = {
      val metadataUrl = url(s"$tcEndpoint/builds?locator=buildType:(id:bt410),branch:(default:any,name:idea/$build)")
      val tryBuildId = for {
        metadata <- Try(XML.loadString(IO.readLinesURL(metadataUrl).mkString))
        buildVal <- Try(metadata \ "build" \\ "@id").map(_.head)
      } yield buildVal.text
      tryBuildId.recoverWith { case exc =>
        Failure(new Error(s"Could not retrieve IDEA/$build build ID from $metadataUrl", exc))
      }.get
    }

    val artifactBaseUrl = s"$tcEndpoint/builds/id:$buildId/artifacts/content"
    val buildMajor = build.substring(0, build.indexOf('.'))
    val downloadTo = baseDir / "archives"
    val unpackTo = baseDir / build

    IO.createDirectory(baseDir)
    IO.createDirectory(downloadTo)
    IO.createDirectory(unpackTo)

    case class Artifact(filename: String, shouldUnpack: Boolean)
    val artifacts = Seq(
      Artifact(s"ideaIC-$buildMajor.SNAPSHOT.win.zip", true),
      Artifact("sources.zip", false)
    )

    artifacts.foreach { case Artifact(filename, shouldUnpack) =>
      val from = url(s"$artifactBaseUrl/$filename")
      val to =
        if (shouldUnpack)
          downloadTo / filename
        else
          unpackTo / filename

      if (to.isFile) {
        streams.log.info(s"$to exists, download aborted")
      } else {
        streams.log.info(s"Downloading $from to $to")
        IO.download(from, to)
      }

      if (shouldUnpack) {
        streams.log.info(s"Unpacking $to")
        IO.unzip(to, unpackTo)
      }
    }
  }
}
