lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "172-EAP-SNAPSHOT"

ideaPluginDirectory in ThisBuild := file("./idea/")

ideaExternalPlugins +=
  IdeaPlugin.Zip("scala-plugin", url("https://plugins.jetbrains.com/files/1347/37646/scala-intellij-bin-2017.2.6.zip"))
