lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "172.3968.1"

ideaDownloadDirectory in ThisBuild := baseDirectory.value / "idea"

ideaExternalPlugins +=
  IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/plugin/download?updateId=38695"))
