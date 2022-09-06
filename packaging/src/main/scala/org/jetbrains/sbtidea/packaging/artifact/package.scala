package org.jetbrains.sbtidea.packaging

import sbt.Keys.TaskStreams

import java.nio.file.FileSystems
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

package object artifact {

  def timed[T](msg: String, f: => T)(implicit streams: TaskStreams): T = {
    val start = System.nanoTime()
    val res = f
    val end = System.nanoTime()
    val duration = Duration.fromNanos(end) - Duration.fromNanos(start)
    streams.log.info(s"(${duration.toMillis}ms) $msg")
    res
  }


  def using[T <: AutoCloseable, V](r: => T)(f: T => V): V = {
    val resource: T = r
    require(resource != null, "resource is null")
    var exception: Throwable = null
    try {
      f(resource)
    } catch {
      case NonFatal(e) =>
        exception = e
        throw e
    } finally {
      if (resource != FileSystems.getDefault)
        closeAndAddSuppressed(exception, resource)
    }
  }

  private def closeAndAddSuppressed(e: Throwable, resource: AutoCloseable): Unit = {
    if (e != null) {
      try {
        resource.close()
      } catch {
        case NonFatal(suppressed) =>
          e.addSuppressed(suppressed)
      }
    } else {
      resource.close()
    }
  }

}
