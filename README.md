# sbt-idea-plugin

[ ![Version](https://api.bintray.com/packages/sbt/sbt-plugin-releases/sbt-idea-plugin/images/download.svg) ](https://bintray.com/jetbrains/sbt-plugins/sbt-idea-plugin/_latestVersion) 
[![Build Status](https://travis-ci.org/jetbrains/sbt-idea-plugin.svg)](https://travis-ci.org/jetbrains/sbt-idea-plugin)
[![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

SBT plugin that makes development of IntelliJ Platform plugins in Scala easier by providing features such as:

- Downloading and attaching IntelliJ Platform binaries
- Setting up the environment for running tests
- Flexible way to define plugin artifact structure
- Publishing the plugin to JetBrains plugin repository

For a comprehensive usage example see [Scala plugin](https://github.com/JetBrains/intellij-scala) or 
[HOCON plugin](https://github.com/JetBrains/intellij-hocon/) build definition.

Note that some features of this plugin may be used independently, i.e. if you only want to [print project structure](#printprojectgraph--taskkeyunit)
or [package artifacts](#packaging) you can depend on:

`"org.jetbrains" % "sbt-declarative-visualizer" % "LATEST_VERSION"` or

`"org.jetbrains" % "sbt-declarative-packaging" % "LATEST_VERSION"`
## Installation

From version 1.0.0, this plugin is published for sbt 0.13 and 1.0

* Insert into `project/plugins.sbt`:

```Scala
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "LATEST_VERSION")
```

* [Enable](#auto-enable-the-plugin) the plugin for your desired projects (your main plugin project and all its dependencies)

* Run SBT and the plugin will automatically download and attach IntelliJ Platform dependencies.

* Start coding

## IntelliJ Platform and plugin

#### `intellijPluginName in ThisBuild :: SettingKey[String]`

Default: `name.in(LocalRootProject).value`

Name of your plugin. Better set this beforehand since several other settings such as
IntelliJ Platform directories and artifact names depend on it.

#### `intellijBuild in ThisBuild :: SettingKey[String]`

Default: `LATEST-EAP-SNAPSHOT`

Selected IDE's build number. Binaries and sources of this build will be downloaded from the
[repository](https://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html) and used in 
compilation and testing. You can find build number of your IntelliJ product in `Help -> About` dialog. However, it might be 
incomplete, so it is strongly recommended to verify it against 
[available releases](https://www.jetbrains.com/intellij-repository/releases) and
[available snapshots](https://www.jetbrains.com/intellij-repository/snapshots).

#### `intellijPlatform in ThisBuild :: SettingKey[IntelliJPlatform]`

Default: `IntelliJPlatform.IdeaCommunity`

Edition of IntelliJ IDE to use in project. Currently available options are:

- IdeaCommunity
- IdeaUltimate
- PyCharmCommunity
- PyCharmProfessional
- CLion
- MPS

#### `intellijDownloadDirectory in ThisBuild :: SettingKey[File]`

Default: `homePrefix / s".${intellijPluginName.value}Plugin${intellijPlatform.value.shortname}" / "sdk"`

Directory where IntelliJ binaries and sources will be downloaded.

#### `intellijDownloadSources in ThisBuild :: SettingKey[Boolean]`

Default: `true`

Flag indicating whether IntelliJ sources should be downloaded alongside binaries or not.

#### `intellijInternalPlugins :: SettingKey[String]`

Default: `Seq.empty`

List of bundled IntelliJ plugins to depend upon. Their jars will be used in compilation.
Available plugins can be found in `intellijBaseDirectory / "plugins"` directory.

#### `intellijExternalPlugins :: SettingKey[IdeaPlugin]`

Default: `Seq.empty`

IntelliJ plugins to depend upon from IJ plugin repository or direct url to plugin artifact.
Plugins from repo can be specified by the plugin's id, optional version and update channel.
Plugins will be checked for compatibility with the `intellijBuild` you specified and updated to the latest version unless
 some specific version is given explicitly. [How to find plugin's id](https://github.com/JetBrains/sbt-idea-plugin/wiki/How-to-find-plugin's-id).

```SBT
// use Scala plugin as a dependency
intellijExternalPlugins += "org.intellij.scala".toPlugin
// use Scala plugin version 2019.2.1
intellijExternalPlugins += "org.intellij.scala:2019.2.1".toPlugin
// use latest nightly build from the repo
intellijExternalPlugins += "org.intellij.scala::Nightly".toPlugin
// use specific version from Eap update channel
intellijExternalPlugins += "org.intellij.scala:2019.3.2:Eap".toPlugin
```

#### `jbrVersion :: Option[String]`

Default: `Some(JbrInstaller.VERSION_AUTO)`

JetBrains Java runtime version to use when running the IDE with the plugin. By default JBR version is extracted from
IDE installation metadata. Only jbr 11 is supported. Available versions can be found on [jbr bintray](https://bintray.com/jetbrains/intellij-jbr/).
To disable, set to `None`

#### `patchPluginXml :: SettingKey[pluginXmlOptions]`

Default: `pluginXmlOptions.DISABLED`

Define some [`plugin.xml`](https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_configuration_file.html)
fields to be patched when building the artifact. Only the file in `target` folder
is patched, original sources are left intact. Available options are:

```SBT
patchPluginXml := pluginXmlOptions { xml =>
  xml.version           = version.value
  xml.pluginDescription = "My cool IDEA plugin"
  xml.changeNotes       = sys.env("CHANGE_LOG_FROM_CI")
  xml.sinceBuild        = (intellijBuild in ThisBuild).value
  xml.untilBuild        = "193.*"
}
```

#### `intellijVMOptions :: SettingKey[IntellijVMOptions]`

Fine tune java VM options for running the plugin with [`runIDE`](#runide-nopce-nodebug-suspend--inputkeyunit) task.
Example:

```SBT
intellijVMOptions := intellijVMOptions.value.copy(xmx = 2048, xms = 256) 
```

#### `runIDE [noPCE] [noDebug] [suspend] [blocking] :: InputKey[Unit]`

Runs IntelliJ IDE with current plugin. This task is non-blocking by default, so you can continue using SBT console.

By default IDE is run with non-suspending debug agent on port `5005`. This can be overridden by either optional
arguments above, or by modifying default [`intellijVMOptions`](#intellijvmoptions--settingkeyintellijvmoptions). 
[`ProcessCancelledExceptiona`](https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html#background-processes-and-processcanceledexception)
can also be disabled for current run by providing `noPCE` option.

#### `publishPlugin [channel] :: InputKey[String]`

Upload and publish your IntelliJ plugin on https://plugins.jetbrains.com.
In order to publish to the repo you need to 
[obtain permanent token](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/api/plugin_upload.html) and
either place it into `~/.ij-plugin-repo-token` file or pass via `IJ_PLUGIN_REPO_TOKEN` env or java property.

This task also expects an optional argument - a [custom release channel](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/custom_channels.html).
If omitted, plugin will be published to the default plugin repository channel (Stable) 
 

#### `updateIntellij :: TaskKey[Unit]`

This task is run automatically when sbt project is loaded. 
Downloads IntelliJ's binaries and sources, puts them into
`intellijBaseDirectory` directory. Also downloads or updates external plugins.

#### `buildIntellijOptionsIndex :: TaskKey[Unit]`

Builds index of options provided by the plugin to make them searchable via 
[search everywhere](https://www.jetbrains.com/help/idea/searching-everywhere.html#search_settings) action.
This task should either be manually called instead of `packageArtifact` or before `packageArtifactZip` since it patches 
jars already built by `packageArtifact`.

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
This is later used by `publishPlugin` as an artifact to upload.

## Utils

#### `printProjectGraph :: TaskKey[Unit]`

Prints ASCII graph of currently selected project to console. Useful for debugging complex builds.
![](https://user-images.githubusercontent.com/1345782/65074597-d8172880-d99d-11e9-947e-aa74855e9ff1.png)

## Running the plugin

### From SBT

To run the plugin from SBT simply use [runIDE](#runide-nopce-nodebug-suspend-blocking--inputkeyunit) task.
Your plugin will be automatically compiled, an artifact built and attached to new IntelliJ instance.

Debugger can later be attached to the process remotely - the default port is 5005.

### From IDEA

To run your plugin from IDEA, one needs to define a synthetic runner project in the build.
This can be achieved with the helper function `createRunnerProject` which will set it up based on the root
project of the plugin. Example:
```SBT
lazy val ideaRunner = createRunnerProject(scalaCommunity)
```

- `sbt-idea-plugin` generates IDEA-readable artifact xml and run configuration using `createIDEARunConfiguration` and
`createIDEAArtifactXml` tasks
- :exclamation: At the moment, to correctly import the project in IDEA  please use "Import with sbt shell" option.
 Another `SBT Refresh` action might be necessary after the _initial_ import. This will automatically run the generators
  above
- If you don't want to use sbt shell, or automatic generation didn't work, you can manually run
 `$YOUR_RUNNER_PROJECT/createIDEARunConfiguration` and `$YOUR_PLUGIN_PROJECT/createIDEAArtifactXml` tasks
- After artifact and run configuration have been created(they're located in `.idea` folder of the project) you can 
run or debug the new run configuration. This will compile the project, build the artifact and attach it to the
 new IDEA instance
- Note that doing an "SBT Refresh" (or manually running the tasks above) is required after making changes to your build
that affect the final artifact(i.e. changing `libraryDependencies`), in order to update IDEA configs
  
## Custom IntelliJ artifacts repo

Under some circumstances using a proxy may be required to access IntelliJ artifacts repo, or there even is a local
artifact mirror set up. To use non-default repository for downloading IntelliJ product distributions set 
`sbtidea.ijrepo` jvm property. Example: `-Dsbtidea.ijrepo=https://proxy.mycompany.com/intellij-repository`
  
## Auto enable the plugin

Sbt-idea-plugin currently breaks scalaJS compilation, and thereby has autoloading disabled.
To enable it either add `enablePlugins(SbtIdeaPlugin)` to project definition. Example:
```scala
lazy val hocon = project.in(file(".")).settings(
  scalaVersion  := "2.12.8",
  version       := "2019.1.2",
  intellijInternalPlugins := Seq("properties"),
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
).enablePlugins(SbtIdeaPlugin)
```

If you with to automatically enable the plugin for all projects in your build, place the following class into top level 
`project` folder of your build.
```scala
import org.jetbrains.sbtidea.AbstractSbtIdeaPlugin

object AutoSbtIdeaPlugin extends AbstractSbtIdeaPlugin {
  override def trigger  = allRequirements
}
``` 