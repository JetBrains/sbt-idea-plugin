package org.jetbrains.sbtidea.packaging

case class RevisionReference(
  repositoryUrl: String,
  revision: String,
  message: String
)