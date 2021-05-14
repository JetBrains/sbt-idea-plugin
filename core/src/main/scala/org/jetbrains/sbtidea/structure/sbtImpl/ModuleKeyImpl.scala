package org.jetbrains.sbtidea.structure.sbtImpl

import org.jetbrains.sbtidea.structure.{ModuleKey, ProjectScalaVersion}
import sbt.ModuleID

case class ModuleKeyImpl(id: ModuleID, attributes: Map[String, String]) extends ModuleKey {
  def ~==(other: ModuleKey): Boolean = other match {
    case ModuleKeyImpl(otherId, _) =>
      id.organization == otherId.organization && id.name == otherId.name
    case _ => false
  }

  override def org: String = id.organization
  override def name: String = id.name
  override def revision: String = id.revision

  override def hashCode(): Int = id.organization.hashCode

  // this is a crime against programming, such micro-optimisations are not worth the time spent
  // debugging obscure bugs caused by misbehaving maps and sets
  override def equals(o: scala.Any): Boolean = o match {
    case ModuleKeyImpl(_id, _attributes) =>
      id.organization.equals(_id.organization) &&
        (id.name == _id.name || id.name.matches(_id.name)) &&
        (id.revision == _id.revision || id.revision.matches(_id.revision)) &&
        attributes == _attributes
    case _ => false
  }

  override def toString: String = s"$id[${if (attributes.nonEmpty) attributes.toString else ""}]"
}