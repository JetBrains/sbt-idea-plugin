
lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaBuild in ThisBuild := "15.0.1"

ideaPluginDirectory in ThisBuild := baseDirectory.value / "idea"

ideaEdition in ThisBuild := IdeaEdition.Ultimate
