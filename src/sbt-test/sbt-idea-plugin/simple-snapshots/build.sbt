lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "172-EAP-SNAPSHOT"

ideaDownloadDirectory in ThisBuild := baseDirectory.value / "idea"

ideaExternalPlugins +=
  IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/plugin/download?updateId=37646"))
