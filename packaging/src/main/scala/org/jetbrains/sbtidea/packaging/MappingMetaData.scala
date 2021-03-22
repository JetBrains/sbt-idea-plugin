package org.jetbrains.sbtidea.packaging

import org.jetbrains.sbtidea.packaging.ExcludeFilter.ExcludeFilter

case class MappingMetaData(shading: Seq[ShadePattern], excludeFilter: ExcludeFilter, static: Boolean, project: Option[String], kind: MAPPING_KIND.MAPPING_KIND)
object     MappingMetaData { val EMPTY: MappingMetaData = MappingMetaData(Seq.empty, ExcludeFilter.AllPass, static = true, project = None, kind = MAPPING_KIND.UNDEFINED) }