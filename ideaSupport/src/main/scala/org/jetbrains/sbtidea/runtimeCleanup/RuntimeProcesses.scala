package org.jetbrains.sbtidea.runtimeCleanup

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters.*
import scala.util.Try

/** Best effort only: a JVM can start after this snapshot. No attach API or JVM agent is used. */
private[sbtidea] object RuntimeProcesses {
  def activePaths(): Option[Set[Path]] = Try {
    val user = ProcessHandle.current().info().user()
    val processes = ProcessHandle.allProcesses()
    try {
      val results = processes.iterator().asScala.filter(_.isAlive).flatMap { process =>
        val info = process.info()
        if (user.isPresent && info.user().isPresent && user.get() != info.user().get()) None
        // ProcessHandle also exposes kernel/zombie/inaccessible non-Java entries with no
        // executable. They cannot be classified as relevant JVMs; treating all of them as
        // unknown Java processes would permanently disable cleanup on ordinary machines.
        else if (!info.command().isPresent) None
        else {
          val executable = Paths.get(info.command().get()).getFileName.toString.toLowerCase(java.util.Locale.ROOT)
          if (executable != "java" && executable != "java.exe") None
          else Some(if (info.arguments().isPresent) pathsIn(info.arguments().get().toSeq)
                    else if (process.isAlive) None else Some(Set.empty[Path]))
        }
      }.toVector
      if (results.exists(_.isEmpty)) None else Some(results.flatten.flatten.toSet)
    } finally processes.close()
  }.toOption.flatten

  def pathsIn(arguments: Seq[String]): Option[Set[Path]] = Try[Set[Path]] {
    def expand(args: Seq[String], depth: Int): Seq[String] = {
      require(depth <= 4, "Nested Java argument files")
      args.flatMap {
        case argument if argument.startsWith("@@") => Seq(argument.drop(1))
        case argument if argument.startsWith("@") =>
          val file = Paths.get(argument.drop(1))
          require(file.isAbsolute && Files.size(file) <= 1024 * 1024, "Unreadable or oversized Java argument file")
          expand(tokenize(new String(Files.readAllBytes(file), UTF_8)), depth + 1)
        case argument => Seq(argument)
      }
    }
    expand(arguments, 0).collect {
      case arg if arg.startsWith("-Didea.system.path=") || arg.startsWith("-Didea.config.path=") =>
        val path = Paths.get(arg.substring(arg.indexOf('=') + 1))
        require(path.isAbsolute, "Relative runtime directory in Java process")
        path.toFile.getCanonicalFile.toPath
    }.toSet
  }.toOption

  /** The quoting/comment rules used by Java @argfiles, including generated paths with spaces. */
  private def tokenize(text: String): Seq[String] = {
    val tokens = Vector.newBuilder[String]
    val token = new StringBuilder
    var quote: Char = 0
    var started = false
    var i = 0
    def finish(): Unit = if (started) { tokens += token.toString; token.clear(); started = false }
    while (i < text.length) {
      val c = text.charAt(i)
      if (quote == 0 && c == '#') {
        while (i < text.length && text.charAt(i) != '\n') i += 1
        finish()
      } else if (quote == 0 && c.isWhitespace) finish()
      else if (c == '\'' || c == '"') {
        if (quote == 0) { quote = c; started = true }
        else if (quote == c) quote = 0
        else token += c
      } else if (quote != 0 && c == '\\' && i + 1 < text.length) {
        i += 1
        text.charAt(i) match {
          case 'n' => token += '\n'
          case 'r' => token += '\r'
          case 't' => token += '\t'
          case 'f' => token += '\f'
          case '\r' | '\n' =>
            while (i + 1 < text.length && text.charAt(i + 1).isWhitespace) i += 1
          case escaped => token += escaped
        }
        started = true
      } else { token += c; started = true }
      i += 1
    }
    require(quote == 0, "Unterminated quote in Java argument file")
    finish()
    tokens.result()
  }
}
