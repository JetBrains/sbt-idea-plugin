package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.runIdea.{CustomIntellijVMOptions, IntellijVMOptions, IntellijVMOptionsBuilder}
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder.VmOptions

// Package-local access for observing the same builders used by runIDE and indexing.
object RuntimeOptionsProbe {
  def build(builder: IntellijVMOptionsBuilder, useNew: Boolean,
            current: CustomIntellijVMOptions, legacy: IntellijVMOptions): Seq[String] = {
    val options = if (useNew) VmOptions.New(current) else VmOptions.Old(legacy)
    builder.build(options, forTests = false, quoteValues = false)
  }
}
