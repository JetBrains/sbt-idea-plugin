package org.jetbrains.sbtidea

import scala.collection.mutable.ArrayBuffer

trait PluginLogger {
  def debug(msg: =>String): Unit
  def info(msg: =>String): Unit
  def warn(msg: =>String): Unit
  def error(msg: =>String): Unit
  def fatal(msg: =>String): Unit
}

object PluginLogger extends PluginLogger {
  private[this] class BufferLogger extends PluginLogger {
    private val (lDEBUG, lINFO, lWARN, lERR, lFATAL) = (0, 1, 2, 3, 4)
    private val buffer = ArrayBuffer[(Int, String)]()
    override def debug(msg: => String): Unit = buffer += lDEBUG -> msg
    override def info(msg: => String): Unit = buffer += lINFO -> msg
    override def warn(msg: => String): Unit = buffer += lWARN -> msg
    override def error(msg: => String): Unit = buffer += lERR -> msg
    override def fatal(msg: => String): Unit = buffer += lFATAL -> msg
    def flush(actualLogger: PluginLogger): Unit = {
      buffer.foreach { case (level, msg) =>
        level match {
          case `lDEBUG` => actualLogger.debug(msg)
          case `lINFO` => actualLogger.info(msg)
          case `lWARN` => actualLogger.warn(msg)
          case `lERR` => actualLogger.error(msg)
          case `lFATAL`  => actualLogger.fatal(msg)
        }
      }
      buffer.clear()
    }

  }

  private val isInUnitTest: Boolean = {
    val stacktrace = new RuntimeException().getStackTrace
    stacktrace.exists(_.getClassName.contains("org.scalatest.tools.Runner"))
  }

  private var instance: PluginLogger =
    if (isInUnitTest) new ConsoleLogger
    else new BufferLogger

  def bind(actualLogger: PluginLogger): PluginLogger = {
    assert(actualLogger != this, "Can't assign logger instance to self")
    val savedInstance = instance
    instance match {
      case bl: BufferLogger =>
        bl.flush(actualLogger)
        instance = actualLogger
      case _ =>
        instance = actualLogger
    }
    savedInstance
  }
  override def debug(msg: => String): Unit = instance.debug(msg)
  override def info(msg: => String): Unit  = instance.info(msg)
  override def warn(msg: => String): Unit  = instance.warn(msg)
  override def error(msg: => String): Unit = instance.error(msg)
  override def fatal(msg: => String): Unit = instance.fatal(msg)
}

