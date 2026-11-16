package org.jetbrains.sbtidea.runtimeCleanup

import org.jetbrains.sbtidea.{CapturingLogger, CheckoutRuntimeDirectories, TmpDirUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.StandardOpenOption.{CREATE, WRITE}
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.Properties
import scala.collection.JavaConverters.*
import scala.concurrent.duration.*

class RuntimeCleanupTest extends AnyFunSuite with Matchers with TmpDirUtils {
  import RuntimeCleanup.*
  private val id = "IU-263.7307.200"

  private class Fixture {
    val home = newTmpDir.resolve("plugin home")
    val checkout = newTmpDir.resolve("checkout with spaces")
    val resolver = CheckoutRuntimeDirectories(home, checkout.toFile)
    val tests = resolver.tests(id)
    val index = resolver.buildIndex(id)
    val paths = Map("test.system" -> tests.system, "test.config" -> tests.config,
      "index.system" -> index.system, "index.config" -> index.config)
    var time: Instant = Instant.parse("2026-11-16T12:00:00Z")
    val log = new CapturingLogger()
    val policy = Policy(true, 7.days, 1.day)
    def service(p: Policy = policy, active: () => Option[Set[Path]] = () => Some(Set.empty),
                remove: Path => Unit = path => Files.delete(path)): RuntimeCleanup =
      new RuntimeCleanup(home, p, log, () => time, active, remove)
    val cleanup = service()
    def marker: Path = cleanup.marker(checkout, id)
    def advance(duration: FiniteDuration): Unit = time = time.plusNanos(duration.toNanos)
    def file(path: Path, content: String = "keep"): Path = {
      Files.createDirectories(path.getParent); Files.write(path, content.getBytes(UTF_8)); path
    }
    def register(): Unit = cleanup.refresh(checkout, id, paths)
    def edit(key: String, value: String): Unit = {
      val p = new Properties
      val reader = Files.newBufferedReader(marker, UTF_8)
      try p.load(reader) finally reader.close()
      p.setProperty(key, value)
      val writer = Files.newBufferedWriter(marker, UTF_8)
      try p.store(writer, "test") finally writer.close()
      Files.setLastModifiedTime(marker, FileTime.from(time.minusSeconds(8.days.toSeconds)))
    }
  }

  test("registration creates only metadata, touch preserves content, and retention has a strict boundary") {
    val f = new Fixture
    f.register()
    f.paths.values.foreach(path => Files.exists(path) shouldBe false)
    val content = Files.readAllBytes(f.marker)
    f.advance(1.hour)
    f.register()
    Files.readAllBytes(f.marker) shouldBe content
    Files.getLastModifiedTime(f.marker).toInstant shouldBe f.time
    f.file(f.tests.system.resolve("cache"), "12345")
    f.advance(7.days)
    f.cleanup.cleanupIfDue().directories shouldBe 0
    f.advance(1.day)
    val result = f.cleanup.cleanupIfDue()
    result.directories shouldBe 1
    result.bytes shouldBe 5L
    Files.exists(f.marker) shouldBe false
    Files.exists(f.marker.resolveSibling(f.marker.getFileName.toString.replace(".properties", ".lock"))) shouldBe true
  }

  test("cleanup preserves development, legacy, SDK, downloads and unrelated unregistered runtimes") {
    val f = new Fixture
    f.paths.values.foreach(path => f.file(path.resolve("cache")))
    val keep = Seq("test-system/keep", "test-config/keep", "sdk/downloads/keep", "sdk/263.7307.200/keep",
      s"runtime/$id/${f.resolver.checkoutHash}/dev/system/keep", s"runtime/$id/${f.resolver.checkoutHash}/dev/config/keep",
      s"runtime/$id/${f.resolver.checkoutHash}/dev/client/system/keep", "runtime/config-shared/keep",
      "runtime/old-checkout/old-build-tests/system/keep", "runtime/unrelated/checkout/tests/system/keep")
      .map(path => f.file(f.home.resolve(path)))
    f.register(); f.advance(8.days)
    f.cleanup.cleanupIfDue().directories shouldBe 4
    keep.foreach(path => new String(Files.readAllBytes(path), UTF_8) shouldBe "keep")
  }

  test("retention and opt-out belong to the owning marker, not to the cleaning session") {
    val f = new Fixture
    f.file(f.tests.system.resolve("cache"))
    f.service(Policy(true, 30.days, 1.day)).refresh(f.checkout, id, f.paths)
    f.advance(8.days)
    f.service(Policy(true, 1.day, 1.day)).cleanupIfDue().directories shouldBe 0
    f.advance(24.days)
    f.cleanup.cleanupIfDue().directories shouldBe 1

    f.file(f.tests.system.resolve("cache"))
    f.service(Policy(false, 7.days, 1.day)).refresh(f.checkout, id, f.paths)
    f.advance(90.days)
    f.cleanup.cleanupIfDue().directories shouldBe 0
    Files.exists(f.tests.system.resolve("cache")) shouldBe true
  }

  test("persisted throttle is shared across services; disabled and repeated checks do no process scanning") {
    val f = new Fixture
    f.register(); f.advance(8.days)
    var inspections = 0
    def active(): Option[Set[Path]] = { inspections += 1; Some(Set.empty) }
    f.service(Policy(false, 7.days, 1.day), () => active()).cleanupIfDue()
    inspections shouldBe 0
    f.service(active = () => active()).cleanupIfDue()
    inspections shouldBe 1
    f.register(); Files.setLastModifiedTime(f.marker, FileTime.from(f.time.minusSeconds(8.days.toSeconds)))
    f.service(active = () => active()).cleanupIfDue()
    inspections shouldBe 1
    f.advance(1.day)
    f.service(active = () => active()).cleanupIfDue()
    inspections shouldBe 2
  }

  test("active or uninspectable processes defer removal, including stale data in the completeness edge case") {
    val f = new Fixture
    f.file(f.tests.system.resolve("cache")); f.register(); f.advance(8.days)
    // Ordinarily sbt refreshed this marker recently. This covers IDEA-only use after a long absence.
    f.service(active = () => Some(Set(f.tests.system))).cleanupIfDue().directories shouldBe 0
    f.advance(1.day)
    f.service(active = () => None).cleanupIfDue().directories shouldBe 0
    f.advance(1.day)
    f.cleanup.cleanupIfDue().directories shouldBe 1
  }

  test("global and per-record locks skip competing cleanup or refresh without deleting data") {
    val f = new Fixture
    f.file(f.tests.system.resolve("cache")); f.register(); f.advance(8.days)
    val locks = Seq(f.home.resolve(".sbt-idea-plugin/runtime-cleanup.lock"),
      f.marker.resolveSibling(f.marker.getFileName.toString.replace(".properties", ".lock")))
    locks.foreach { path =>
      val channel = FileChannel.open(path, CREATE, WRITE)
      val lock = channel.lock()
      try f.service().cleanupIfDue().directories shouldBe 0
      finally { lock.release(); channel.close() }
      Files.exists(f.tests.system.resolve("cache")) shouldBe true
      f.advance(1.day)
    }
    f.service().cleanupIfDue().directories shouldBe 1
  }

  test("independent explicit overrides withdraw previous ownership, including exact old default paths") {
    val f = new Fixture
    f.paths.values.foreach(path => f.file(path.resolve("cache")))
    f.register()
    val before = Files.getLastModifiedTime(f.marker)
    f.cleanup.withdraw(Set(f.tests.system))
    Files.getLastModifiedTime(f.marker) shouldBe before
    f.advance(8.days)
    f.cleanup.cleanupIfDue().directories shouldBe 3
    Files.exists(f.tests.system.resolve("cache")) shouldBe true
  }

  test("overlapping overrides preserve whole managed directories for the rare development reuse case") {
    // Reusing a disposable test directory for persistent development data is unusual;
    // cover it for completeness, including a nested directory and a containing root.
    Seq("exact", "nested", "parent", "symlink").foreach { kind =>
      val f = new Fixture
      f.paths.values.foreach(path => f.file(path.resolve("cache")))
      f.register()
      val selected = kind match {
        case "exact" => f.tests.system
        case "nested" => f.tests.system.resolve("development")
        case "parent" => f.tests.system.getParent
        case "symlink" => Files.createSymbolicLink(newTmpDir.resolve("alias"), f.tests.system)
      }
      f.cleanup.withdraw(Set(selected))
      f.advance(8.days)
      // Another checkout's cleaner sees the persisted ownership withdrawal too.
      f.service().cleanupIfDue().directories shouldBe (if (kind == "parent") 2 else 3)
      Files.exists(f.tests.system.resolve("cache")) shouldBe true
    }
  }

  test("unknown formats, unsafe paths, future timestamps and symlinked roots are left untouched") {
    Seq("formatVersion" -> "99", "owner" -> "someone-else", "test.system" -> "../outside/system",
      "test.system" -> "/absolute/system", "test.system" -> "runtime/build/checkout/dev/system").foreach { case (key, value) =>
      val f = new Fixture
      f.file(f.tests.system.resolve("cache")); f.register(); f.advance(8.days); f.edit(key, value)
      f.cleanup.cleanupIfDue().directories shouldBe 0
      Files.exists(f.tests.system.resolve("cache")) shouldBe true
    }
    val future = new Fixture
    future.register(); future.file(future.tests.system.resolve("cache"))
    Files.setLastModifiedTime(future.marker, FileTime.from(future.time.plusSeconds(1.day.toSeconds)))
    future.cleanup.cleanupIfDue().directories shouldBe 0

    val linked = new Fixture
    linked.register()
    val target = newTmpDir
    linked.file(target.resolve("keep"))
    Files.createDirectories(linked.tests.system.getParent)
    Files.createSymbolicLink(linked.tests.system, target)
    linked.advance(8.days)
    linked.cleanup.cleanupIfDue().directories shouldBe 0
    Files.exists(target.resolve("keep")) shouldBe true
  }

  test("nested symlinks never delete their targets and partial failures count only successful file deletions") {
    val f = new Fixture
    val target = f.file(newTmpDir.resolve("keep"), "outside")
    f.file(f.tests.system.resolve("removed"), "123")
    f.file(f.tests.system.resolve("blocked"), "12345")
    Files.createSymbolicLink(f.tests.system.resolve("link"), target.getParent)
    f.register(); f.advance(8.days)
    val report = f.service(remove = path => {
      if (path.getFileName.toString == "blocked") throw new java.io.IOException("simulated access failure")
      Files.delete(path)
    }).cleanupIfDue()
    report.directories shouldBe 0
    report.bytes shouldBe 3L
    report.failures should be >= 1
    Files.exists(f.marker) shouldBe true
    Files.exists(target) shouldBe true
    f.advance(1.day)
    f.cleanup.cleanupIfDue().bytes shouldBe 5L
  }

  test("hourly task activity reuses context, separate builds stop refreshing old records, invalidation stops activity") {
    val f = new Fixture
    val maintenance = new RuntimeMaintenance(f.home, f.resolver, f.policy, new Object, f.log,
      () => f.time, () => Some(Set.empty))
    maintenance.initializeOverrides(Set.empty)
    maintenance.register(id, f.paths, Roles)
    val original = Files.getLastModifiedTime(f.marker)
    f.advance(30.minutes); maintenance.afterBatch()
    Files.getLastModifiedTime(f.marker) shouldBe original
    f.advance(30.minutes); maintenance.afterBatch()
    Files.getLastModifiedTime(f.marker).toInstant shouldBe f.time
    val secondId = "IU-263.7307.8"
    val second = f.resolver.tests(secondId)
    maintenance.register(secondId, Map("test.system" -> second.system), TestRoles)
    val first = Files.getLastModifiedTime(f.marker)
    f.advance(2.hours); maintenance.afterBatch()
    Files.getLastModifiedTime(f.marker) shouldBe first
    Files.getLastModifiedTime(f.cleanup.marker(f.checkout, secondId)).toInstant shouldBe f.time
    maintenance.invalidate(); f.advance(2.hours); maintenance.afterBatch()
    Files.getLastModifiedTime(f.cleanup.marker(f.checkout, secondId)).toInstant should not be f.time
  }

  test("positive duration settings are required") {
    intercept[IllegalArgumentException](Policy(true, Duration.Zero, 1.day))
    intercept[IllegalArgumentException](Policy(true, 7.days, -1.second))
  }

  test("canonical checkout aliases share a record while other checkouts and builds expire independently") {
    val f = new Fixture
    Files.createDirectories(f.checkout)
    val alias = Files.createSymbolicLink(newTmpDir.resolve("alias"), f.checkout)
    f.cleanup.marker(alias, id) shouldBe f.marker
    f.register(); f.file(f.tests.system.resolve("old"))
    f.advance(8.days)
    val otherCheckout = newTmpDir
    val other = CheckoutRuntimeDirectories(f.home, otherCheckout.toFile).tests(id)
    f.file(other.system.resolve("keep"))
    f.cleanup.refresh(otherCheckout, id, Map("test.system" -> other.system))
    val nextBuild = "IU-263.7307.201"
    val next = f.resolver.tests(nextBuild)
    f.file(next.system.resolve("keep"))
    f.cleanup.refresh(alias, nextBuild, Map("test.system" -> next.system))
    f.cleanup.cleanupIfDue().directories shouldBe 1
    Files.exists(other.system.resolve("keep")) shouldBe true
    Files.exists(next.system.resolve("keep")) shouldBe true
  }

  test("supported records clean their recorded older layout without assuming the current hierarchy") {
    val f = new Fixture
    val older = f.home.resolve(s"runtime/${f.resolver.checkoutHash}/$id-tests/system")
    f.file(older.resolve("cache"))
    f.cleanup.refresh(f.checkout, id, Map("test.system" -> older))
    f.advance(8.days)
    f.cleanup.cleanupIfDue().directories shouldBe 1
    Files.exists(older) shouldBe false
  }

  test("an override observed in one subproject cannot be re-enrolled by another default consumer") {
    val f = new Fixture
    val maintenance = new RuntimeMaintenance(f.home, f.resolver, f.policy, new Object, f.log,
      () => f.time, () => Some(Set.empty))
    f.file(f.tests.system.resolve("keep")); f.file(f.tests.config.resolve("cache"))
    f.register()
    maintenance.initializeOverrides(Set(f.tests.system.resolve("development")))
    maintenance.register(id, f.paths, Roles)
    f.advance(8.days)
    f.cleanup.cleanupIfDue().directories shouldBe 1
    Files.exists(f.tests.system.resolve("keep")) shouldBe true
  }

  test("maintenance waits for override discovery and defers deletion when withdrawal cannot acquire a record lock") {
    val f = new Fixture
    f.file(f.tests.system.resolve("settings")); f.file(f.index.system.resolve("expired"))
    f.register(); f.advance(8.days)
    val maintenance = new RuntimeMaintenance(f.home, f.resolver, f.policy, new Object, f.log,
      () => f.time, () => Some(Set.empty))
    val currentId = "IU-current-build"
    val current = f.resolver.tests(currentId)
    def registerCurrent(): Unit = maintenance.register(currentId, Map("test.system" -> current.system), TestRoles)
    registerCurrent(); maintenance.afterBatch()
    Files.exists(f.index.system.resolve("expired")) shouldBe true
    Files.exists(f.cleanup.marker(f.checkout, currentId)) shouldBe false

    val channel = FileChannel.open(f.marker.resolveSibling(f.marker.getFileName.toString.replace(".properties", ".lock")), CREATE, WRITE)
    val lock = channel.lock()
    try {
      maintenance.initializeOverrides(Set(f.tests.system))
      maintenance.overridesReady shouldBe false
      registerCurrent(); maintenance.afterBatch()
      Files.exists(f.tests.system.resolve("settings")) shouldBe true
      Files.exists(f.index.system.resolve("expired")) shouldBe true
    } finally { lock.release(); channel.close() }

    maintenance.initializeOverrides(Set(f.tests.system))
    maintenance.overridesReady shouldBe true
    registerCurrent(); maintenance.afterBatch()
    Files.exists(f.tests.system.resolve("settings")) shouldBe true
    Files.exists(f.index.system.resolve("expired")) shouldBe false
  }

  test("an actual Java process using a generated argument file protects an expired runtime") {
    val f = new Fixture
    f.file(f.tests.system.resolve("cache")); f.register(); f.advance(8.days)
    val ready = newTmpDir.resolve("ready")
    val classes = java.nio.file.Paths.get(classOf[RuntimeProcessProbe].getProtectionDomain.getCodeSource.getLocation.toURI)
    val args = f.file(newTmpDir.resolve("runtime arguments.txt"),
      s""""-Didea.system.path=${f.tests.system}"
         |-cp
         |"$classes"
         |${classOf[RuntimeProcessProbe].getName}
         |"$ready"
         |""".stripMargin)
    val javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString
    val process = new ProcessBuilder(javaExecutable, "@" + args).inheritIO().start()
    try {
      val deadline = System.nanoTime() + 10.seconds.toNanos
      while (!Files.exists(ready) && process.isAlive && System.nanoTime() < deadline) Thread.sleep(10)
      Files.exists(ready) shouldBe true
      val arguments = process.toHandle.info().arguments()
      arguments.isPresent shouldBe true
      RuntimeProcesses.pathsIn(arguments.get().toSeq).get should contain(f.tests.system.toFile.getCanonicalFile.toPath)
      f.service(active = () => RuntimeProcesses.activePaths()).cleanupIfDue().directories shouldBe 0
      Files.exists(f.tests.system.resolve("cache")) shouldBe true
    } finally { process.destroyForcibly(); process.waitFor() }
  }

  test("process arguments and Java argfiles recognize quoted and escaped paths, failing conservatively") {
    val f = new Fixture
    val args = f.file(newTmpDir.resolve("args with spaces.txt"),
      s"""# generated
         |"-Didea.system.path=${f.tests.system}"
         |-Didea.config.path="${f.tests.config}"
         |""".stripMargin)
    RuntimeProcesses.pathsIn(Seq("@" + args)) shouldBe Some(Set(f.tests.system, f.tests.config).map(_.toFile.getCanonicalFile.toPath))
    RuntimeProcesses.pathsIn(Seq(s"-Didea.system.path=${f.tests.system}")) shouldBe Some(Set(f.tests.system.toFile.getCanonicalFile.toPath))
    RuntimeProcesses.pathsIn(Seq("@relative-file")) shouldBe None
    RuntimeProcesses.pathsIn(Seq("@" + args.resolveSibling("missing"))) shouldBe None
    f.file(args, "\"unterminated")
    RuntimeProcesses.pathsIn(Seq("@" + args)) shouldBe None
  }

  private def inspectOne(cleanup: RuntimeCleanup): RuntimeCleanupDiagnostics.Inspection =
    cleanup.inspect().entries.head.result.toOption.get

  test("diagnostics use each owner's policy and the same strict expiry boundary as deletion") {
    import RuntimeCleanupAssessment.*
    val f = new Fixture
    val owner = f.service(Policy(true, 30.days, 1.day))
    owner.refresh(f.checkout, id, f.paths)
    f.advance(8.days)
    inspectOne(f.cleanup).age shouldBe Fresh(f.time.plusSeconds(22.days.toSeconds))
    f.advance(22.days)
    inspectOne(f.cleanup).age.eligible shouldBe false
    f.advance(1.second)
    inspectOne(f.cleanup).age.eligible shouldBe true
    owner.refresh(f.checkout, id, f.paths)
    Files.setLastModifiedTime(f.marker, FileTime.from(f.time.plusSeconds(1)))
    inspectOne(f.cleanup).age shouldBe FutureDated
    f.service(Policy(false, 7.days, 1.day)).refresh(f.checkout, id, f.paths)
    f.advance(90.days)
    inspectOne(f.cleanup).age shouldBe OptedOut
  }

  test("diagnostics take one process snapshot only for expired candidates and explain process protection") {
    import RuntimeCleanupAssessment.*
    val f = new Fixture
    var scans = 0
    var active: Option[Set[Path]] = Some(Set.empty)
    val cleanup = f.service(active = () => { scans += 1; active })
    f.register()
    cleanup.inspect(); scans shouldBe 0
    val secondId = "IU-263.7307.201"
    cleanup.refresh(f.checkout, secondId, Map("test.system" -> f.resolver.tests(secondId).system))
    f.advance(8.days)
    cleanup.inspect().entries.foreach(_.result.toOption.get.protection shouldBe Some(NoMatchingProcess))
    scans shouldBe 1
    // The same parent/child matching protects both inspection and actual deletion.
    active = Some(Set(f.tests.system.resolve("log")))
    inspectOne(cleanup).protection shouldBe Some(MatchingProcess)
    active = None
    cleanup.inspect().entries.foreach(_.result.toOption.get.protection shouldBe Some(ProcessInformationUnavailable))
    scans shouldBe 3
    inspectOne(f.service(active = () => throw new java.io.IOException("process information denied")))
      .protection shouldBe Some(ProcessInformationUnavailable)
  }

  test("inspection is read-only and does not postpone an otherwise due cleanup") {
    val f = new Fixture
    f.file(f.tests.system.resolve("cache")); f.register(); f.advance(8.days)
    def contents(): Map[Path, (FileTime, Vector[Byte])] = {
      val stream = Files.walk(f.home)
      try stream.iterator().asScala.map { path =>
        path -> (Files.getLastModifiedTime(path),
          (if (Files.isRegularFile(path)) Files.readAllBytes(path).toVector else Vector.empty[Byte]))
      }.toMap finally stream.close()
    }
    val before = contents()
    f.cleanup.inspect()
    contents() shouldBe before // Includes markers, checkpoint, lock files and directory mtimes.
    f.cleanup.cleanupIfDue().directories shouldBe 1
  }

  test("inspection of an absent home or registry creates no files and performs no process scan") {
    val f = new Fixture
    val cleanup = f.service(active = () => fail("no expired candidates"))
    cleanup.inspect().entries shouldBe empty
    Files.exists(f.home) shouldBe false
    f.file(f.home.resolve("runtime/unregistered/system/keep"))
    cleanup.inspect().entries shouldBe empty
    Files.exists(f.home.resolve(".sbt-idea-plugin")) shouldBe false
  }

  test("diagnostics distinguish registered absent paths, present paths and unregistered roles") {
    val f = new Fixture
    f.file(f.tests.system.resolve("cache"))
    f.cleanup.refresh(f.checkout, id, Map("test.system" -> f.tests.system, "test.config" -> f.tests.config))
    val snapshot = f.cleanup.inspect()
    val paths = snapshot.entries.head.result.toOption.get.paths.map(path => path.role -> path.existence).toMap
    paths shouldBe Map("test.system" -> "present", "test.config" -> "absent")
    f.cleanup.withdraw(Set(f.tests.system))
    val rendered = RuntimeCleanupDiagnostics.render(f.cleanup.inspect(), f.checkout, None).mkString("\n")
    rendered should include("test.system: not registered")
    rendered should include("index.system: not registered")
    rendered should include("test.config:")
    rendered should include("[absent]")
    rendered should not include "overridden"
  }

  test("diagnostics explain unsupported, malformed and unsafe records without changing them") {
    Seq(
      ("formatVersion", "99", "unsupported formatVersion"),
      ("owner", "someone-else", "unrecognized owner"),
      ("retentionSeconds", "wrong", "invalid retentionSeconds"),
      ("automaticCleanup", "perhaps", "Invalid automaticCleanup"),
      ("test.system", "../outside/system", "relative path"),
      ("test.system", "runtime/build/checkout/dev/system", "unsafe test.system"),
      ("extra", "unknown", "unknown fields: extra"),
    ).foreach { case (key, value, reason) =>
      val f = new Fixture
      f.register(); f.edit(key, value)
      val before = Files.readAllBytes(f.marker)
      f.cleanup.inspect().entries.head.result.left.toOption.get should include(reason)
      Files.readAllBytes(f.marker) shouldBe before
    }
    val f = new Fixture
    f.register()
    Files.createDirectories(f.tests.system.getParent)
    val outside = newTmpDir
    Files.createSymbolicLink(f.tests.system, outside)
    f.cleanup.inspect().entries.head.result.left.toOption.get should include("symbolic link")
    val malformed = f.file(f.cleanup.entries.resolve("malformed.properties"), "bad=\\uNOPE")
    f.cleanup.inspect().entries.find(_.file == malformed).get.result.isLeft shouldBe true
  }

  test("diagnostics explain unavailable registries and invalid checkpoints without creating locks") {
    val f = new Fixture
    Files.createDirectories(f.cleanup.entries.getParent)
    Files.createSymbolicLink(f.cleanup.entries, newTmpDir)
    f.cleanup.inspect().registryProblem.get should include("symbolic link")
    Files.exists(f.home.resolve(".sbt-idea-plugin/runtime-cleanup.lock")) shouldBe false

    val other = new Fixture
    val checkpoint = other.home.resolve(".sbt-idea-plugin/runtime-cleanup-v1.properties")
    other.file(checkpoint, "lastAttempt=invalid\n")
    val snapshot = other.cleanup.inspect()
    snapshot.lastAttempt.isLeft shouldBe true
    snapshot.eligibleAt shouldBe other.time
    Files.exists(checkpoint.resolveSibling("runtime-cleanup.lock")) shouldBe false
    other.file(checkpoint, s"lastAttempt=${other.time.plusSeconds(1.day.toSeconds)}\n")
    other.cleanup.inspect().eligibleAt shouldBe other.time // Future checkpoints do not suppress passes.
  }

  test("diagnostics include persisted and in-memory throttles without probing locks") {
    val f = new Fixture
    f.cleanup.cleanupIfDue()
    f.advance(1.hour)
    val snapshot = f.service().inspect()
    snapshot.lastAttempt shouldBe Right(Some(f.time.minusSeconds(1.hour.toSeconds)))
    snapshot.eligibleAt shouldBe f.time.plusSeconds(23.hours.toSeconds)

    val other = new Fixture
    val lockFile = other.file(other.home.resolve(".sbt-idea-plugin/runtime-cleanup.lock"), "")
    val channel = FileChannel.open(lockFile, WRITE)
    val lock = channel.lock()
    try {
      other.cleanup.cleanupIfDue() // A failed lock acquisition sets only the cheap local deadline.
      val observed = other.cleanup.inspect()
      observed.lastAttempt shouldBe Right(None)
      observed.eligibleAt shouldBe other.time.plusSeconds(1.day.toSeconds)
    } finally { lock.release(); channel.close() }
  }

  test("report output is stable and states the limits of the snapshot and normal sbt lifecycle") {
    val f = new Fixture
    f.register()
    f.cleanup.refresh(f.checkout, "IU-111", Map("test.system" -> f.resolver.tests("IU-111").system))
    val snapshot = f.cleanup.inspect()
    snapshot.entries.map(_.file.getFileName.toString) shouldBe snapshot.entries.map(_.file.getFileName.toString).sorted
    val rendered = RuntimeCleanupDiagnostics.render(snapshot, f.checkout, Some(id)).mkString("\n")
    rendered should include(s"Cached runtime context: $id")
    rendered should include("Recorded retention: 7 days")
    rendered should include("lock contention and previous skip decisions are not recorded")
    rendered should include("Normal sbt startup/completion hooks may refresh usage or run automatic cleanup")
    rendered should include("No size scan is performed")
    val disabled = snapshot.copy(policy = Policy(false, 7.days, 1.day))
    RuntimeCleanupDiagnostics.render(disabled, f.checkout, Some(id)).mkString("\n") should include("Next pass eligible: disabled")
  }
}
