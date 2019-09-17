package org.jetbrains.sbtidea.packaging

import java.nio.file.FileSystems

import sbt.Keys.TaskStreams

import scala.util.control.NonFatal

package object artifact {

  def timed[T](msg: String, f: => T)(implicit streams: TaskStreams): T = {
    val start = System.currentTimeMillis()
    val res = f
    streams.log.info(s"(${System.currentTimeMillis() - start}ms) $msg")
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
