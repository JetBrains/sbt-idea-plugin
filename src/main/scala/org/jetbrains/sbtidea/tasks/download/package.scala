package org.jetbrains.sbtidea.tasks

import java.net.URL

import org.jetbrains.sbtidea.Keys._

package object download {

  object ArtifactKind extends Enumeration {
    type ArtifactKind = Value
    val IDEA_DIST, IDEA_SRC, PLUGIN_ZIP, PLUGIN_JAR, MISC = Value
  }

  case class BuildInfo(buildNumber: String, edition: IdeaEdition)

  case class ArtifactPart(url: URL,
                          kind: ArtifactKind.ArtifactKind,
                          nameHint: String = "",
                          optional: Boolean = false)
}
