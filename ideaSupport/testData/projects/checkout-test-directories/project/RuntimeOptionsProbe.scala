package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijVMOptionsBuilder}
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.VmOptions

// Package-local access for observing the same builders used by runIDE and indexing.
object RuntimeOptionsProbe {
  def build(builder: IntellijVMOptionsBuilder, options: CustomIntellijVMOptions): Seq[String] =
    builder.build(VmOptions.New(options), forTests = false, quoteValues = false)
}
