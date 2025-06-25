package org.jetbrains.sbtidea

import sbt.Keys.*

class SbtPluginLogger(streams: TaskStreams) extends PluginLogger {
  override def debug(msg: => String): Unit = streams.log.debug(msg)
  override def info(msg: => String): Unit = streams.log.info(msg)
  override def warn(msg: => String): Unit = streams.log.warn(msg)
  override def error(msg: => String): Unit = streams.log.error(msg)
  override def fatal(msg: => String): Unit = fatalErrors.synchronized {
    streams.log.error(msg)
    fatalErrors += msg ;
  }

  private val fatalErrors = scala.collection.mutable.ListBuffer[String]()

  def throwFatalErrors(): Unit = fatalErrors.synchronized {
    if (fatalErrors.nonEmpty) {
      val message = fatalErrors.mkString("\n")
      fatalErrors.clear()
      throw new sbt.MessageOnlyException(message)
    }
  }
}
