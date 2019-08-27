package org.jetbrains.sbtidea.packaging.artifact

trait ArtifactBuilder[T, U] {
    def produceArtifact(structure: T): U
}