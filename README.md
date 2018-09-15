# sbt-idea-plugin

[ ![Version](https://api.bintray.com/packages/sbt/sbt-plugin-releases/sbt-idea-plugin/images/download.svg) ](https://bintray.com/jetbrains/sbt-plugins/sbt-idea-plugin/_latestVersion) 
[![Build Status](https://travis-ci.org/jetbrains/sbt-idea-plugin.svg)](https://travis-ci.org/jetbrains/sbt-idea-plugin)
[![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

SBT plugin that makes development of IntelliJ IDEA plugins in Scala easier by providing features such as:

- Downloading and attaching IDEA binaries
- Setting up the environment for running tests
- Flexible way to define plugin artifact structure
- Publishing the plugin to JetBrains plugin repository

For a comprehensive usage example see [Scala plugin](https://github.com/JetBrains/intellij-scala) build definition.

## Installation

From version 1.0.0, this plugin is published for sbt 0.13 and 1.0

* Insert into `project/plugins.sbt`:

```Scala
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "2.1.3")
```

* Run SBT and the plugin will automatically download and attach IDEA dependencies.

* Start coding

## IDEA and plugin settings

#### `ideaPluginName in ThisBuild :: SettingKey[String]`

Default: `"MyCoolIdeaPlugin"`

Name of your plugin. Better set this beforehand since several other settings such as
IDEA directries and artifact names depend on it.

#### `ideaBuild in ThisBuild :: SettingKey[String]`

Default: `LATEST-EAP-SNAPSHOT`

IDEA's build number. Binaries and sources of this build will be downloaded from https://jetbrains.com and used in 
compilation and testing. You can find build number of your IDEA in `Help -> About` dialog. However, it might be 
incomplete, so I strongly recommend you to verify it against 
[available releases](https://www.jetbrains.com/intellij-repository/releases) and
[available snapshots](https://www.jetbrains.com/intellij-repository/snapshots).

#### `ideaEdition in ThisBuild :: SettingKey[IdeaEdition]`

Default: `IdeaEdition.Community`

Edition of IntelliJ IDEA to use in project.

#### `ideaDownloadDirectory in ThisBuild :: SettingKey[File]`

Default: `homePrefix / s".${ideaPluginName.value}Plugin${ideaEdition.value.shortname}" / "sdk"`

Directory where IDEA binaries and sources will be downloaded.

#### `ideaDownloadSources in ThisBuild :: SettingKey[Boolean]`

Default: `true`

Flag indicating whether IDEA sources should be downloaded alongside IDEA
binaries or not.

#### `ideaInternalPlugins :: SettingKey[String]`

Default: `Seq.empty`

List of bundled IDEA plugins to depend upon. Their jars will be used in compilation.
Available plugins can be found in `ideaBaseDirectory / "plugins"` directory.

#### `ideaExternalPlugins :: SettingKey[IdeaPlugin]`

Default: `Seq.empty`

List of external IDEA plugins to depend upon. Their zips or jars will be downloaded
and unpacked in `ideaBaseDirectory / "externalPlugins"` directory, each in its own subdirectory. They will be used
in compilation.

```SBT
// use some particular artifact
ideaExternalPlugins += IdeaPlugin.Zip("Scala", "https://plugins.jetbrains.com/...")
// use latest nightly build from the repo
ideaExternalPlugins += IdeaPlugin.Id("Scala", "org.intellij.scala", Some("Nightly"))
```

#### `ideaPublishSettings :: SettingKey[PublishSettings]`

Default: `PublishSettings(pluginId = "", username = "", password = "", channel = None)`

Settings necessary for uploading your IDEA plugin to https://plugins.jetbrains.com

## Packaging settings

#### `packageMethod :: SettingKey[PackagingMethod]`

Default for root project: `PackagingMethod.Standalone(targetPath = s"lib/${name.value}.jar")`

Default for all other subprojects: `PackagingMethod.MergeIntoParent()`

Controls how current project will be treated when packaging the plugin artifact.
```SBT
// produce standalone jar with the same name as the project:
packageMethod := PackagingMethod.Standalone()

// put all classes of this project into parent's jar
// NB: this option supports transitive dependencies on projects: it will walk up the dependency 
// tree to find the first Standalone() project, however if your project has multiple such parents
// this will result in an error - in this case use MergeIntoOther(project: Project) to expicitly
// specify in which project to merge into
packageMethod := PackagingMethod.MergeIntoParent()

// merge all dependencies of this project in a standalone jar
// being used together with assembleLibraries setting allows sbt-assembly like packaging
// the project may contain classes but they will be ignored during packaging
packageMethod := PackagingMethod.DepsOnly("lib/myProjectDeps.jar")
assembleLibraries := true

// skip project alltogether during packaging
packageMethod := PackagingMethod.Skip()
```

#### `packageLibraryMappings :: SettingKey[Seq[(ModuleID, Option[String])]]`

Default:
```SBT
"org.scala-lang"  % "scala-.*" % ".*" -> None ::
"org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil
```

Sequence of rules to fine-tune how the library dependencies are packaged. By default all dependencies
including transitive are placed in the "lib" subfolder of the plugin artifact. Scala runtime
is also skipped by default.

```SBT
// package ALL dependencies to default location
packageLibraryMappings := Seq()

// merge all scalameta jars into a single jar
packageLibraryMappings += "org.scalameta" %% ".*" % ".*" -> Some("lib/scalameta.jar")

// skip packaging protobuf
packageLibraryMappings += "com.google.protobuf" % "protobuf-java" % ".*" -> None

// rename scala library(strip version suffix)
packageLibraryMappings += "org.scala-lang" % "scala-library" % scalaVersion -> Some("lib/scala-library.jar")
```

#### `packageFileMappings :: SettingKey[Seq[(File, String)]]`

Default: `Seq.empty`

Defines mappings for adding custom files to the artifact or even override files inside jars.
Target path is considered to be relative to `packageOutputDir`.

```SBT
// copy whole folder recursively to artifact root
packageFileMappings += target.value / "repo" -> "repo/"

// package single file info a jar
packageFileMappings += "resources" / "ILoopWrapperImpl.scala" ->
                            "lib/jps/repl-interface-sources.jar"
                            
// overwrite some file inside already existing jar of the artifact
packageFileMappings +=  "resources" / "META-INF" / "plugin.xml" ->
                            "lib/scalaUltimate.jar!/META-INF/plugin.xml"                            
``` 

#### `packageAdditionalProjects :: SettingKey[Seq[Project]]`

Default: `Seq.empty`

By default the plugin builds artifact structure based on internal classpath dependencies
of the projects in an SBT build(`dependsOn(...)`). However, sometimes one may need to package
a project that no other depends upon. This setting is used to explicitly tell the plugin which 
projects to package into the artifact without a need to introduce unwanted classpath dependency.

#### `shadePatterns :: SettingKey[Seq[ShadePattern]]`

Default: `Seq.empty`

Class shading patterns to be applied by JarJar library. Used to resolve name clashes with 
libraries from IntelliJ platform such as protobuf.

```SBT
shadePatterns += ShadePattern("com.google.protobuf.**", "zinc.protobuf.@1")
```

## Tasks

#### `packagePlugin :: TaskKey[File]`

Builds unpacked plugin distribution. This task traverses dependency graph of the
build and uses settings described in the section above to create sub-artifact structure 
for each project. By default all child projects' classes are merged into the root project jar,
which is placed into the "lib" folder of the plugin artifact, all library dependencies
including transitive are placed in the "lib" folder as well. 
 
 
#### `packagePluginZip :: TaskKey[File]`

Produces ZIP file from the artifact produced by `packagePlugin` task.
This is later used by publishPlugin as an artifact to upload.

#### `publishPlugin :: TaskKey[String]`

Upload and publish your IDEA plugin on https://plugins.jetbrains.com. Returns
URL of published plugin.

#### `updateIdea :: TaskKey[Unit]`

This task is run when sbt project is loaded. 
Download IDEA's binaries and sources, put them into
`ideaBaseDirectory` directory. Download external plugins and put
them in `ideaBaseDirectory / "externalPlugins"` directory. Automatically add IDEA's and
plugin's jars into `unmanagedJars in Compile`.

## Running the plugin

To run IDEA with the plugin being developed, one needs to define a synthetic runner project in the build.
This can be achieved with the helper function `createRunnerProject` which will set it up based on the root
project of the plugin and a new name. Example:
```SBT
lazy val ideaRunner = createRunnerProject(scalaCommunity, "idea-runner")
```

There are two ways to run/debug your plugin: from SBT and from IDEA

- Running from bare sbt is as simple as invoking `run` task. Debugger can later be attached to the
  process, the default port is 5005.
- Running from IDEA requires first invoking `$YOUR_RUNNER_PROJECT/createIDEARunConfiguration` task.
  A new run configuration should appear in your local IDEA which can be launched via "Run" or "Debug"

## Notes and best practices

- If you use `sbt-assembly` plugin to produce a fat jar to distribute your plugin you should avoid putting IDEA's jars 
  into this fat jar of yours. To achieve this insert

  ```Scala
  assemblyExcludedJars in assembly := ideaFullJars.value
  ```

  into your `build.sbt`
  
- If you depend upon one or more external plugins, add

  ```
  -Didea.plugins.path=$PROJECT_DIR$/<ideaBaseDirectory>/externalPlugins
  ```

  to your run configuration's VM options in order for debug IDEA instance to use
  already downloaded plugins.
  
