package org.pantsbuild.jarjar

import scala.collection.JavaConverters.seqAsJavaListConverter

class NiceJJProcessor(rules: Seq[PatternElement]) extends MainProcessor(rules.asJava, false, false)
