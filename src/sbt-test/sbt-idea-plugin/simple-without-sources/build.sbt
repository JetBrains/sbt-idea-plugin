lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "15.0"

ideaPluginDirectory in ThisBuild := file("./idea/")

ideaDownloadSources in ThisBuild := false
