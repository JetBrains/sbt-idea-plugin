package org.jetbrains.sbtidea.runIdea

import org.jetbrains.sbtidea.*
import org.jetbrains.sbtidea.download.{IntelliJVersionDetector, Version}
import sbt.pathToPathOps

import java.nio.file.Path
import scala.annotation.nowarn
import scala.collection.JavaConverters.*
import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered

/**
  * @param ideaHome          this path will be used as idea home when you run IDEA or unit tests<br>
  *                          example: {{{ <userHome>/.ScalaPluginIU }}}
  * @param intellijDirectory example: {{{ <userHome>/.ScalaPluginIU/sdk/223.6160 }}}
  */
@nowarn("cat=deprecation")
case class IntellijVMOptions(platform: IntelliJPlatform,
                             pluginPath: Path,
                             ideaHome: Path,
                             intellijDirectory: Path,
                             xmx: Int = 2048,
                             xms: Int = 128,
                             reservedCodeCacheSize: Int = 512,
                             softRefLRUPolicyMSPerMB: Int = 50,
                             gc: String = "-XX:+UseG1GC",
                             gcOpt: String = "-XX:CICompilerCount=2",
                             debug: Boolean = true,
                             debugPort: Int = 5005,
                             suspend: Boolean = false,
                             test: Boolean = false,
                             defaultOptions: Seq[String] = IntellijVMOptions.DEFAULT_STATIC_OPTS) {
  def withOption(opt: String): IntellijVMOptions = copy(defaultOptions = defaultOptions :+ opt)
}

object IntellijVMOptions {

  implicit class VMOptionOps(val options: IntellijVMOptions) extends AnyVal {
    import options.*

    private def build(quoteValues: Boolean): Seq[String] = {
      val intellijVersion = IntelliJVersionDetector.detectIntellijVersion(intellijDirectory.toFile)

      def OQ(str: String): String = if (quoteValues) str.xmlQuote else str
      val buffer = new mutable.ArrayBuffer[String]()
      buffer ++= defaultOptions

      val jnaFolderName = System.getProperty("os.arch") match {
        case "aarch64" => "aarch64"
        case _         => "amd64" //currently there are only two possible folders in `lib/jna`
      }

      //if the version is not detected, assume as if it's the latest
      if (intellijVersion.forall(_ > Version("223.6160"))) {
        val pty4jFolderPath = (intellijDirectory / "lib/pty4j").toString.replace("\\", "/")
        val jnaFolderPath = (intellijDirectory / "lib/jna" / jnaFolderName).toString.replace("\\", "/")
        buffer += s"-Dpty4j.preferred.native.folder=${OQ(pty4jFolderPath)}"
        buffer += s"-Djna.boot.library.path=${OQ(jnaFolderPath)}"
        buffer += s"-Djna.nounpack=true"
        buffer += s"-Djna.nosys=true"
      }

      buffer +=  s"-Xms${xms}m"
      buffer +=  s"-Xmx${xmx}m"
      buffer +=  s"-XX:ReservedCodeCacheSize=${reservedCodeCacheSize}m"
      buffer +=  s"-XX:SoftRefLRUPolicyMSPerMB=$softRefLRUPolicyMSPerMB"
      buffer +=  gc
      buffer +=  gcOpt
      val (system, config) =
        if (test) (ideaHome.resolve("test-system"), ideaHome.resolve("test-config"))
        else      (ideaHome.resolve("system"), ideaHome.resolve("config"))
      buffer += s"-Didea.system.path=${OQ(system.toString)}"
      buffer += s"-Didea.config.path=${OQ(config.toString)}"
      buffer += s"-Dplugin.path=${OQ(pluginPath.toString)}"
      if(test) {
        buffer += "-Didea.use.core.classloader.for.plugin.path=true"
        buffer += "-Didea.force.use.core.classloader=true"
      }
      if (!test)
        buffer += "-Didea.is.internal=true"
      if (debug) {
        val suspendValue = if (suspend) "y" else "n"
        buffer += s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendValue,address=$debugPort"
      }
      if (platform.platformPrefix.nonEmpty)
        buffer += s"-Didea.platform.prefix=${platform.platformPrefix}"
      buffer
    }

    def add(opts: Seq[String]): IntellijVMOptions = copy(defaultOptions = defaultOptions ++ opts)
    def add(opt: String): IntellijVMOptions = copy(defaultOptions = defaultOptions :+ opt)

    def asSeq(quoteValues: Boolean = false): Seq[String] = build(quoteValues).filter(_.nonEmpty)
    def asJava(quoteValues: Boolean = false): java.util.List[String] = asSeq(quoteValues).asJava
  }

  val IDEA_MAIN = "com.intellij.idea.Main"

  val USE_PATH_CLASS_LOADER = "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader"

  /**
    * @note -Dsun.io.useCanonCaches & -Dsun.io.useCanonPrefixCache are disabled by default in JDK 17,
    *       but we still explicitly pass `false` just in case (e.g. if JBR 11 is used)
    */
  val DEFAULT_STATIC_OPTS: Seq[String] =
    """-Dsun.io.useCanonCaches=false
      |-Dsun.io.useCanonPrefixCache=false
      |-ea
      |-Djava.net.preferIPv4Stack=true
      |-XX:+HeapDumpOnOutOfMemoryError
      |-XX:-OmitStackTraceInFastThrow
      |-Dawt.useSystemAAFontSettings=lcd
      |-Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine
      |-Dsun.tools.attach.tmp.only=true
      |-Dide.no.platform.update=true
      |-Dkotlinx.coroutines.debug=off
      |-Djdk.attach.allowAttachSelf=true
      |-XX:MaxJavaStackTraceDepth=10000
      |-Didea.debug.mode=true
      |-Dapple.laf.useScreenMenuBar=true
      |-Duse.linux.keychain=false
      |-Didea.initially.ask.config=true
      |
      |-Djdk.module.illegalAccess.silent=true
      |-XX:+IgnoreUnrecognizedVMOptions
      |
      |-XX:CompileCommand=exclude,com/intellij/openapi/vfs/impl/FilePartNodeRoot,trieDescend
      |
      |--add-opens=java.base/java.io=ALL-UNNAMED
      |--add-opens=java.base/java.lang=ALL-UNNAMED
      |--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
      |--add-opens=java.base/java.net=ALL-UNNAMED
      |--add-opens=java.base/java.nio=ALL-UNNAMED
      |--add-opens=java.base/java.nio.charset=ALL-UNNAMED
      |--add-opens=java.base/java.text=ALL-UNNAMED
      |--add-opens=java.base/java.time=ALL-UNNAMED
      |--add-opens=java.base/java.util=ALL-UNNAMED
      |--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
      |--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
      |--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED
      |--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
      |--add-opens=java.base/sun.nio.fs=ALL-UNNAMED
      |--add-opens=java.base/sun.security.ssl=ALL-UNNAMED
      |--add-opens=java.base/sun.security.util=ALL-UNNAMED
      |--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED
      |--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED
      |--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED
      |--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED
      |--add-opens=java.desktop/java.awt=ALL-UNNAMED
      |--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED
      |--add-opens=java.desktop/java.awt.event=ALL-UNNAMED
      |--add-opens=java.desktop/java.awt.image=ALL-UNNAMED
      |--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED
      |--add-opens=java.desktop/javax.swing=ALL-UNNAMED
      |--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED
      |--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED
      |--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED
      |--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED
      |--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED
      |--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED
      |--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED
      |--add-opens=java.desktop/sun.awt=ALL-UNNAMED
      |--add-opens=java.desktop/sun.font=ALL-UNNAMED
      |--add-opens=java.desktop/sun.java2d=ALL-UNNAMED
      |--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED
      |--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED
      |--add-opens=java.desktop/sun.swing=ALL-UNNAMED
      |--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED
      |--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
      |--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED
      |--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED
      |""".stripMargin
      .replace("\r", "") //needed in case sbt-idea-plugin is build locally on windows
      .split("\n")
      .map(_.trim) // trimming just in case of accidental trailing spaces in sources
      .filter(_.nonEmpty)
      .toSeq
}
