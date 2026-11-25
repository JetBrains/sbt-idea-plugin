import org.jetbrains.sbtidea.Keys._
import org.jetbrains.sbtidea.productInfo.{Launch, OS, ProductInfo}
import org.jetbrains.sbtidea.IntellijTestRuntimeDirectories.RuntimeDirectories
import org.jetbrains.sbtidea.runIdea.IntellijVMOptionsBuilder
import org.jetbrains.sbtidea.RuntimeOptionsProbe

// Directory probes need neither an IntelliJ installation nor a compiled plugin.
Global / onLoad := identity
ThisBuild / scalaVersion := "2.12.21"
ThisBuild / intellijBuild := "243.22562.145"
ThisBuild / intellijPluginName := "DirectoryProbe"
ThisBuild / productInfo := ProductInfo("Mock IDEA", "2024.3", None,
  sys.props.getOrElse("directoryProbe.build", "243.22562.145"), "IU", Nil,
  Seq(Launch(OS.current, if (System.getProperty("os.arch") == "aarch64") "aarch64" else "amd64",
    "bin/idea", None, "bin/probe.vmoptions", None, Nil, Nil)), Nil)

val probe = taskKey[Unit]("Fork a lightweight JVM with the real Test / forkOptions")
val dumpPaths = taskKey[Unit]("Record resolved test settings")
val assertMarkers = taskKey[Unit]("Verify markers were created by the preceding preparation task")
// Read the actual internal task values without exposing additional public plugin APIs.
val devBuilder = TaskKey[IntellijVMOptionsBuilder]("intellijVMOptionsBuilder")
val baseBuilder = TaskKey[IntellijVMOptionsBuilder]("baseIntellijVMOptionsBuilder")
val indexDirectories = TaskKey[RuntimeDirectories]("intellijBuildIndexDirectories")

lazy val commonSettings = Seq(
  name := thisProject.value.id,
  ideaConfigOptions := IdeaConfigBuildingOptions(generateRunConfigForSplitMode = true),
  Test / classDirectory := (ThisBuild / baseDirectory).value / "probe classes",
  customIntellijVMOptions := customIntellijVMOptions.value.withExtraOptions(Seq(
    "-Didea.system.path=stale", "-Didea.config.path=stale", "-Didea.log.path=stale")),
  Test / customIntellijVMOptions := (Test / customIntellijVMOptions).value
    .withExtraOptions(Seq("-Dscope.marker=test", "-Didea.system.path=stale", "-Didea.config.path=stale", "-Didea.log.path=stale")),
  probe := {
    val opts = (Test / forkOptions).value
    val output = baseDirectory.value / "observed.properties"
    val peerFile = (ThisBuild / baseDirectory).value / "peer.txt"
    val peer = if (peerFile.isFile) IO.read(peerFile) else ""
    val result = Fork.java(opts, Seq("-cp", (Test / classDirectory).value.getAbsolutePath,
      "DirectoryProbe", output.getAbsolutePath, peer))
    require(result == 0, s"Probe failed: $result")
  },
  assertMarkers := {
    Seq((Test / intellijTestSystemDir).value, (Test / intellijTestConfigDir).value).foreach { dir =>
      require((dir / "README.txt").isFile, s"Missing checkout marker in $dir")
    }
  },
  dumpPaths := {
    val properties = new java.util.Properties
    properties.setProperty("sbt.version", sbtVersion.value)
    properties.setProperty("java.home", System.getProperty("java.home"))
    properties.setProperty("idea.system.path", (Test / intellijTestSystemDir).value.getAbsolutePath)
    properties.setProperty("idea.config.path", (Test / intellijTestConfigDir).value.getAbsolutePath)
    properties.setProperty("idea.log.path", ((Test / intellijTestSystemDir).value / "log").getAbsolutePath)
    properties.setProperty("idea.plugins.path", ((Test / intellijTestConfigDir).value / "plugins").getAbsolutePath)
    properties.setProperty("sdk", intellijBaseDirectory.value.getAbsolutePath)
    properties.setProperty("downloads", artifactsDownloadsDir.value.getAbsolutePath)
    properties.setProperty("dev.system", intellijDevSystemDir.value.getAbsolutePath)
    properties.setProperty("dev.config", intellijDevConfigDir.value.getAbsolutePath)
    properties.setProperty("client.system", intellijDevClientSystemDir.value.getAbsolutePath)
    properties.setProperty("client.config", intellijDevClientConfigDir.value.getAbsolutePath)
    properties.setProperty("index.system", indexDirectories.value.system.toString)
    properties.setProperty("index.config", indexDirectories.value.config.toString)
    properties.setProperty("sharing", intellijDevUseSharedDirectories.value.toString)
    Seq("dev" -> devBuilder.value, "index" -> baseBuilder.value.withRuntimeDirectories(indexDirectories.value))
      .foreach { case (prefix, builder) =>
        val options = RuntimeOptionsProbe.build(builder, customIntellijVMOptions.value)
        Seq("idea.system.path", "idea.config.path", "idea.log.path", "idea.paths.selector", "idea.properties.file").foreach { key =>
          val matches = options.filter(_.startsWith("-D" + key + "="))
          require(matches.size == 1, s"Duplicate or missing $key: $matches")
          properties.setProperty(prefix + "." + key, matches.head.split("=", 2)(1))
        }
      }
    val out = new java.io.FileOutputStream(baseDirectory.value / "settings.properties")
    try properties.store(out, "Resolved directory settings") finally out.close()
  },
)

lazy val root = project.in(file(".")).enablePlugins(SbtIdeaPlugin).settings(commonSettings)
lazy val child = project.in(file("child")).enablePlugins(SbtIdeaPlugin).settings(commonSettings)
