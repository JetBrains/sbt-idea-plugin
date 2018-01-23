lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin)

ideaDownloadDirectory in ThisBuild := baseDirectory.value / "idea"

ideaBuild in ThisBuild := "173.4548.10"

ideaEdition in ThisBuild := IdeaEdition.Ultimate
