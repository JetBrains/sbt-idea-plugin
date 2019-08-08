package org.jetbrains.sbtidea.tasks.packaging.structure.sbtImpl

import org.jetbrains.sbtidea.tasks.packaging.structure.ModuleKey
import sbt.ModuleID

case class ModuleKeyImpl(id: ModuleID, attributes: Map[String, String]) extends ModuleKey {
  def ~==(other: ModuleKey): Boolean = other match {
    case ModuleKeyImpl(otherId, _) =>
      id.organization == otherId.organization && id.name == otherId.name
  }

  override def org: String = id.organization
  override def name: String = id.name
  override def revision: String = id.revision

  override def hashCode(): Int = id.organization.hashCode

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