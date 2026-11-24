package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijVMOptionsBuilder}

// Package-local access for observing the same builders used by runIDE and indexing.
object RuntimeOptionsProbe {
  def build(builder: IntellijVMOptionsBuilder, options: CustomIntellijVMOptions): Seq[String] =
    builder.build(options, forTests = false, quoteValues = false)
}
