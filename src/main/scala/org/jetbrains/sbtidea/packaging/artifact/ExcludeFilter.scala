package org.jetbrains.sbtidea.packaging.artifact

import java.nio.file.Path

object ExcludeFilter {
  type ExcludeFilter = Path=>Boolean

  val AllPass: ExcludeFilter = (_:Path) => false

  def merge(filters: Iterable[ExcludeFilter]): ExcludeFilter =
    path => filters.exists(f => f(path))

}
