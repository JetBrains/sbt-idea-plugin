package ideaplugin

import sbt._
import sbt.Keys._
import IdeaDownloader._


object Tasks {
  def updateIdea(baseDir: File, build: String, streams: TaskStreams): Unit = {

    val buildId = getBuildId(build)
    val buildMajorNum = build.substring(0, build.indexOf('.'))

    val downloadBaseUrl = s"$TeamcityEndpoint/builds/id:$buildId/artifacts/content"
    val downloadDir = baseDir / "archives"
    val unpackDir = baseDir / build

    IO.createDirectory(baseDir)
    IO.createDirectory(downloadDir)
    IO.createDirectory(unpackDir)

    val downloads = Seq(
      Download(
        url(s"$downloadBaseUrl/ideaIC-$buildMajorNum.SNAPSHOT.win.zip"),
        downloadDir / s"ideaIC-$build.zip",
        Some(unpackDir)),
      Download(
        url(s"$downloadBaseUrl/sources.zip"),
        unpackDir / "sources.zip",
        None)
    )

    downloads.foreach(d => downloadArtifact(d, streams))
  }

  def createPluginsClasspath(pluginsBase: File, pluginsUsed: Seq[String]): Classpath = {
    val pluginsDirs = pluginsUsed.foldLeft(PathFinder.empty){ (paths, plugin) =>
      paths +++ (pluginsBase / plugin / "lib")
    }
    (pluginsDirs * (globFilter("*.jar") -- "*asm*.jar")).classpath
  }
}
