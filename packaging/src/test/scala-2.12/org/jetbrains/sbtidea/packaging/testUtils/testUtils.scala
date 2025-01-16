package org.jetbrains.sbtidea.packaging

import java.io.File

package object testUtils {
  val TestDataDir: File = new File("./packaging/testData").getCanonicalFile

  object ReposRevisions {
    val scio: RevisionReference = RevisionReference(
      "https://github.com/spotify/scio-idea-plugin",
      "bbb262c6a2e2f2c20227473632b293e8ad32f4aa",
      "Update sbt-idea-plugin to 4.0.1 (#327)"
    )
    val zioIntellij: RevisionReference = RevisionReference(
      "https://github.com/zio/zio-intellij",
      "13b4fd26a0218bfc1870d094bd6d3c244e33b5b0",
      "Fixing tests"
    )

    // NOTE: it's not an IntelliJ plugin, but for some reason it was added to test, it works though, ok...
    val sbtIdePlugin: RevisionReference = RevisionReference(
      "https://github.com/JetBrains/sbt-idea-plugin",
      "e56f4bb6",
      "introduce usingFileSystem to avoid UnsupportedOperationException with default file system close method"
    )
  }
}
