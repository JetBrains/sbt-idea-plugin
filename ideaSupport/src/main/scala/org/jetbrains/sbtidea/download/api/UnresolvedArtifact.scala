package org.jetbrains.sbtidea.download.api

trait UnresolvedArtifact {
  type U >: this.type <: UnresolvedArtifact
  type R <: ResolvedArtifact

  protected def usedResolver: Resolver[U]

  def resolve: Seq[this.R] = usedResolver.resolve(this)
  def dependsOn: Seq[UnresolvedArtifact]
}