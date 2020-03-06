package org.jetbrains.sbtidea.download.api

import java.net.URL

trait UrlBasedArtifact {
  def dlUrl: URL
}