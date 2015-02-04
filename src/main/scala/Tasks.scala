package ideaplugin

import sbt._
import sbt.Keys._
import scala.language.postfixOps

object Tasks {
  def updateIdea(baseDir: File, version: String, streams: TaskStreams): Unit = {
    IO.createDirectory(baseDir)
    val from = new URL(s"https://download.jetbrains.com/idea/ideaIC-$version.tar.gz")
    val to   = baseDir / "archives" / s"ideaIC-$version.tar.gz"

    if (to.isFile) {
      streams.log.info(s"IDEA IC $version seems to be already downloaded in $to")
    } else {
      streams.log.info(s"Downloading IDEA IC $version from $from")
      IO.download(from, to)
    }

    val unpackTo = baseDir / version
    IO.delete(unpackTo)
    IO.createDirectory(unpackTo)
    streams.log.info(s"Unpacking IDEA IC $version")
    // TODO: make this crossplatform
    s"tar xzf $to -C $unpackTo --strip-components 1"!
  }
}
