package org.jetbrains.sbtidea

import sbt.Keys._

class SbtPluginLogger(streams: TaskStreams) extends PluginLogger {
  override def info(msg: => String): Unit = streams.log.info(msg)
  override def warn(msg: => String): Unit = streams.log.warn(msg)
  override def error(msg: => String): Unit = streams.log.error(msg)
  override def fatal(msg: => String): Unit = { fatalErrors += msg ; streams.log.error(msg)}

  private var fatalErrors = scala.collection.mutable.ListBuffer[String]()
  def throwFatalErrors(): Unit = {
    if (fatalErrors.nonEmpty)
      throw new sbt.MessageOnlyException(fatalErrors.mkString("\n"))
  }
}
