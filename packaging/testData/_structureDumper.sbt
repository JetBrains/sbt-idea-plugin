lazy val dumpStructureToFile = inputKey[Unit]("")
dumpStructureToFile := {
  println("Dumping structure...")
  import org.jetbrains.sbtidea.SbtPluginLogger
  import org.jetbrains.sbtidea.packaging.structure.sbtImpl.{SbtPackagedProjectNodeImpl, SbtPackagingStructureExtractor}

  val rootProject = thisProjectRef.value
  val data = dumpDependencyStructureOffline.?.all(ScopeFilter(inAnyProject)).value.flatten.filterNot(_ == null)
  val buildDeps = buildDependencies.value
  val buildStructure = Keys.buildStructure.value
  val logger = new SbtPluginLogger(streams.value)

  val structureExtractor = new SbtPackagingStructureExtractor(rootProject, data, buildDeps, buildStructure, logger)
  val structure: Seq[SbtPackagedProjectNodeImpl] = structureExtractor.extract

  val baseTargetDirArgOpt = complete.DefaultParsers.spaceDelimited("base target directory").parsed.headOption
  val baseTargetDir = baseTargetDirArgOpt.map(new File(_)).getOrElse(new File("."))

  val projectName = (thisProjectRef / name).value
  val structureFile = new File(baseTargetDir, s"$projectName-structure.dat").getCanonicalFile
  write(structure, structureFile)
  println(s"Structure written to: $structureFile")
  println(s"Package output directory: ${packageOutputDir.value}")
}

def write(value: Any, file: File): Unit = {
  import java.io.{FileOutputStream, ObjectOutputStream}

  val str = new ObjectOutputStream(new FileOutputStream(file))
  str.writeObject(value)
  str.close()
}