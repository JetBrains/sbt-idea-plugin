package org.jetbrains.sbtidea.download

package object idea {
  private val KeepDownloadedFilesPropertyKey = "sbt.idea.plugin.keep.downloaded.files"

  def keepDownloadedFiles: Boolean = System.getProperty(KeepDownloadedFilesPropertyKey) != null
}
