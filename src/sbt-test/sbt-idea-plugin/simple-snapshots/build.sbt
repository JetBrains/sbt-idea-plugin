lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "172-EAP-SNAPSHOT"

ideaExternalPlugins +=
  IdeaPlugin.Zip("scala-plugin", url("https://download.plugins.jetbrains.com/1347/37646/scala-intellij-bin-2017.2.6.zip"))
