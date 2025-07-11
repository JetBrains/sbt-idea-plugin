package org.jetbrains.sbtidea

import java.io.PrintStream

private class CapturingLogger(
  printToStreamOnCapture: Option[PrintStream] = Some(System.out)
) extends PluginLogger {
  import org.jetbrains.sbtidea.CapturingLoggerTest.LogEntry
  import org.jetbrains.sbtidea.CapturingLoggerTest.LogLevel.*
  
  private val messages = new scala.collection.mutable.ArrayBuffer[LogEntry]()

  private def capture(level: LogLevel, msg: => String): Unit = {
    val entry = LogEntry(level, msg)
    messages += entry
    printToStreamOnCapture.foreach(_.println(entry.rendered))
  }

  def getMessages: Seq[LogEntry] =
    messages

  def getMessagesRendered: Seq[String] =
    getMessages.map(_.rendered)

  def getText: String =
    getMessagesRendered.mkString("\n")

  override def debug(msg: => String): Unit = capture(Debug, msg)
  override def info(msg: => String): Unit  = capture(Info, msg)
  override def warn(msg: => String): Unit  = capture(Warn, msg)
  override def error(msg: => String): Unit = capture(Error, msg)
  override def fatal(msg: => String): Unit = capture(Fatal, msg)
}

object CapturingLogger {
  import org.jetbrains.sbtidea.CapturingLoggerTest.{LogEntry, LogLevel}

  def captureLog(f: => Any): Seq[String] =
    captureLogEntriesAndValueAtLeastLevel(LogLevel.values.min)(f)._1.map(_.rendered)

  def captureLogTextAndValue[T](minLevel: LogLevel.Value = LogLevel.Info)(f: => T): (String, T) =
    captureLogEntriesAndValueAtLeastLevel(minLevel)(f) match {
      case (entries, value) =>
        (entries.map(_.rendered).mkString("\n"), value)
    }

  private def captureLogEntriesAndValueAtLeastLevel[T](minLevel: LogLevel.Value)(f: => T): (Seq[LogEntry], T) = {
    val capturingLogger = new CapturingLogger()
    val previousLogger = PluginLogger.bind(capturingLogger)
    val result = f
    PluginLogger.bind(previousLogger)

    val messages = capturingLogger.getMessages
    val messagesFiltered = messages.filter(_.level.id >= minLevel.id)
    messagesFiltered -> result
  }
}


object CapturingLoggerTest {
  
  case class LogEntry(level: LogLevel.LogLevel, message: String) {
    lazy val rendered: String = {
      val levelText = getLevelText(level)
      message.linesIterator.map(line => s"[$levelText] $line").mkString("\n")
    }

    private def getLevelText(level: LogLevel.LogLevel): String =
      level match {
        case LogLevel.Debug => "debug"
        case LogLevel.Info => "info"
        case LogLevel.Warn => "warn"
        case LogLevel.Error => "error"
        case LogLevel.Fatal => "fatal"
      }
  }

  object LogLevel extends Enumeration {
    type LogLevel = Value
    val Debug, Info, Warn, Error, Fatal = Value
  }
}