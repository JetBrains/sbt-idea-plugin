lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "15.0.1"

ideaEdition in ThisBuild := IdeaEdition.Ultimate
