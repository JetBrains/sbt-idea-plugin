lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "15.0"

ideaExternalPlugins +=
  IdeaPlugin.Zip("cucumber-for-java",url("https://plugins.jetbrains.com/files/7212/22304/cucumber-java.zip"))
