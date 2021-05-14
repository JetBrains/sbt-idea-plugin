package org.jetbrains.sbtidea

import java.io.File

package object structure {

  trait ProjectScalaVersion {
    def isDefined: Boolean
    def str: String
  }

  trait ModuleKey {
    def ~==(other: ModuleKey): Boolean
    def org: String
    def name: String
    def revision: String
  }

  trait ProjectNode {
    type T <: ProjectNode
    def name: String
    def id: String
    def parents: Seq[T]
    def children: Seq[T]
    def libs: Seq[Library]
  }

  trait Library {
    def key: ModuleKey
    def jarFile: File
  }

}
