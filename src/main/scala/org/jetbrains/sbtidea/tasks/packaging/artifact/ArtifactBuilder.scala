package org.jetbrains.sbtidea.tasks.packaging.artifact

trait ArtifactBuilder[T, U] {
    def produceArtifact(structure: T): U
}