package org.jetbrains.sbtidea.download.api

trait Resolver[U <: UnresolvedArtifact] {
  def resolve(dep: U): Seq[dep.R]
}