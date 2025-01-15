package org.jetbrains.sbtidea.packaging

case class MappingMetaData(
  shading: Seq[ShadePattern],
  @transient excludeFilter: ExcludeFilter,
  static: Boolean,
  project: Option[String],
  kind: MAPPING_KIND.MAPPING_KIND
)

object MappingMetaData {
  val EMPTY: MappingMetaData = MappingMetaData(
    Seq.empty,
    ExcludeFilter.AllPass,
    static = true,
    project = None,
    kind = MAPPING_KIND.UNDEFINED
  )
}