package org.jetbrains.sbtidea.packaging

import sbt.Keys.TaskStreams

import scala.concurrent.duration.Duration

package object artifact {

  def timed[T](msg: String, f: => T)(implicit streams: TaskStreams): T = {
    val start = System.nanoTime()
    val res = f
    val end = System.nanoTime()
    val duration = Duration.fromNanos(end) - Duration.fromNanos(start)
    streams.log.info(s"(${duration.toMillis}ms) $msg")
    res
  }
}
