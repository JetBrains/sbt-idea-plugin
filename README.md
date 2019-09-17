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

Note that some features of this plugin may be used independently, i.e. if you only want to [print project structure](#printprojectgraph--taskkeyunit)
or [package artifacts](#packaging) you can depend on:

`"org.jetbrains" % "sbt-declarative-visualizer" % "3.0.0"` or

`"org.jetbrains" % "sbt-declarative-packaging" % "3.0.0"`
## Installation

From version 1.0.0, this plugin is published for sbt 0.13 and 1.0

* Insert into `project/plugins.sbt`:

```Scala
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "3.0.0")
```

* [Enable](#auto-enable-the-plugin) the plugin for your desired projects (your main plugin project and all its dependencies)

* Run SBT and the plugin will automatically download and attach IDEA dependencies.

* Start coding

## IDEA and plugin

#### `ideaPluginName in ThisBuild :: SettingKey[String]`

Default: `name.in(LocalRootProject).value`

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

IDEA plugins to depend upon from IJ plugin repository or direct url to plugin artifact.
Plugins from repo can be specified by the plugin's id, optional version and update channel.
Plugins will be checked for compatibility with the `ideaBuild` you specified and updated to the latest version unless
 some specific version is given explicitly. [How to find plugin's id](https://github.com/JetBrains/sbt-idea-plugin/wiki/How-to-find-plugin's-id).

```SBT
// use Scala plugin as a dependency
ideaExternalPlugins += "org.intellij.scala".toPlugin
// use Scala plugin version 2019.2.1
ideaExternalPlugins += "org.intellij.scala:2019.2.1".toPlugin
// use latest nightly build from the repo
ideaExternalPlugins += "org.intellij.scala::Nightly".toPlugin
// use specific version from Eap update channel
ideaExternalPlugins += "org.intellij.scala:2019.3.2:Eap".toPlugin
```

#### `publishPlugin <channel> :: TaskKey[String]`

Upload and publish your IDEA plugin on https://plugins.jetbrains.com.
In order to publish to the repo you need to 
[obtain permanent token](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/api/plugin_upload.html) and
either place it into `~/.ij-plugin-repo-token` file or pass via `IJ_PLUGIN_REPO_TOKEN` env or java property.

This task also expects an optional argument - a [custom release channel](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/custom_channels.html).
If omitted, plugin will be published to the default plugin repository channel (Stable) 
 

#### `updateIdea :: TaskKey[Unit]`

This task is run when sbt project is loaded. 
Download IDEA's binaries and sources, put them into
`ideaBaseDirectory` directory. Download external plugins and put
them in `ideaBaseDirectory / "externalPlugins"` directory. Automatically add IDEA's and
plugin's jars into `unmanagedJars in Compile`.

## Packaging

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

Default for root project: `Seq.empty`

Default for all other projects:
```SBT
"org.scala-lang"  % "scala-.*" % ".*"        -> None ::
"org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil
```

Sequence of rules to fine-tune how the library dependencies are packaged. By default all dependencies
including transitive are placed in the "lib" subfolder of the plugin artifact. 
```SBT
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

#### `packageArtifact :: TaskKey[File]`

Builds unpacked plugin distribution. This task traverses dependency graph of the
build and uses settings described in the section above to create sub-artifact structure 
for each project. By default all child projects' classes are merged into the root project jar,
which is placed into the "lib" folder of the plugin artifact, all library dependencies
including transitive are placed in the "lib" folder as well. 
 
 
#### `packageArtifactZip :: TaskKey[File]`

Produces ZIP file from the artifact produced by `packagePlugin` task.
This is later used by publishPlugin as an artifact to upload.

## Utils

#### `printProjectGraph :: TaskKey[Unit]`

Prints ASCII graph of currently selected project to console. Useful for debugging complex builds.
![](https://user-images.githubusercontent.com/1345782/65074597-d8172880-d99d-11e9-947e-aa74855e9ff1.png)

## Running the plugin

To run IDEA with the plugin being developed, one needs to define a synthetic runner project in the build.
This can be achieved with the helper function `createRunnerProject` which will set it up based on the root
project of the plugin and a new name. Example:
```SBT
lazy val ideaRunner = createRunnerProject(scalaCommunity, "idea-runner")
```

There are two ways to run/debug your plugin: from SBT and from IDEA

- Running from bare sbt is as simple as invoking `run` task on the synthetic runner project. Debugger can later be attached to the process remotely - the default port is 5005.
- Running from IDEA requires first invoking `$YOUR_RUNNER_PROJECT/createIDEARunConfiguration` task.
  A new run configuration should appear in your local IDEA which can be launched via "Run" or "Debug"
  
## Auto enable the plugin

Sbt-idea-plugin currently breaks scalaJS compilation, and thereby has autoloading disabled.
To enable it either add `enablePlugins(SbtIdeaPlugin)` to project definition. Example:
```scala
lazy val hocon = project.in(file(".")).settings(
  scalaVersion  := "2.12.8",
  version       := "2019.1.2",
  ideaInternalPlugins := Seq("properties"),
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
).enablePlugins(SbtIdeaPlugin)
```

If you with to automatically enable the plugin for all projects in your build, place the following class into top level 
`project` folder of your build.
```scala
import org.jetbrains.sbtidea.AbstractSbtIdeaPlugin

object AutoSbtIdeaPlugin extends AbstractSbtIdeaPlugin {
  override def requires = sbt.plugins.JvmPlugin
  override def trigger  = allRequirements
}
``` 