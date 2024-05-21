# sbt-idea-plugin

[ ![Version](https://maven-badges.herokuapp.com/maven-central/org.jetbrains/sbt-idea-plugin/badge.svg) ](https://maven-badges.herokuapp.com/maven-central/org.jetbrains/sbt-idea-plugin) 
![Build Status](https://github.com/jetbrains/sbt-idea-plugin/actions/workflows/scala.yml/badge.svg)
[![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Discord](https://badgen.net/badge/icon/discord?icon=discord&label)](https://discord.gg/aUKpZzeHCK)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/JetBrains/intellij-scala)

SBT plugin that makes development of IntelliJ Platform plugins in Scala easier by providing features such as:

- Downloading and attaching IntelliJ Platform binaries
- Setting up the environment for running tests
- Flexible way to define plugin artifact structure
- Publishing the plugin to JetBrains plugin repository

For a comprehensive usage example see [Scala plugin](https://github.com/JetBrains/intellij-scala) or 
[HOCON plugin](https://github.com/JetBrains/intellij-hocon/) build definition.

A complete list of public IJ plugins implemented in Scala/SBT can be found on [IntelliJ Platform Explorer](https://plugins.jetbrains.com/intellij-platform-explorer/?buildSystem=sbt)

Note that some features of this plugin may be used independently, i.e. if you only want to [print project structure](#printprojectgraph--taskkeyunit)
or [package artifacts](#packaging) you can depend on:

`"org.jetbrains" % "sbt-declarative-visualizer" % "LATEST_VERSION"` or

`"org.jetbrains" % "sbt-declarative-packaging" % "LATEST_VERSION"`

Please see the [Known Issues](#known-issues-and-limitations) section if you come across a problem, and feel free
file a bug on the [Issues](https://github.com/JetBrains/sbt-idea-plugin/issues) page of this repo if you find one.

## Quickstart: IJ Plugin Template Project

To quickly create a Scala based IJ Plugin we provide a template project. Create your own repo on GitHub from the [ JetBrains / **sbt-idea-example** ](https://github.com/JetBrains/sbt-idea-example) template by clicking the green `Use this template` button.
   Clone the sources and open the `build.sbt` via `File | Open` menu in IDEA by choosing `Open as a project`.

## Manual Installation (adding to an already existing sbt build)

From version 1.0.0, this plugin is published for sbt 0.13 and 1.0.
From version 3.17.0, this plugin is published for sbt 1.0 only.

* Insert into `project/plugins.sbt`:

```Scala
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "LATEST_VERSION")
```

* [Enable](#auto-enable-the-plugin) the plugin for your desired projects (your main plugin project and all its dependencies)

* Run SBT and the plugin will automatically download and attach IntelliJ Platform dependencies.

* Start coding

## SBT Related Settings and Tasks

### IntelliJ Platform and Plugin

#### `intellijPluginName in ThisBuild :: SettingKey[String]`

**Default**: `name.in(LocalRootProject).value`

Name of your plugin. Better set this beforehand since several other settings such as
IntelliJ Platform directories and artifact names depend on it. Please see [name troubleshooting](#name-key-in-projects)
for more info.

#### `intellijBuild in ThisBuild :: SettingKey[String]`

**Default**: `LATEST-EAP-SNAPSHOT`

Selected IDE's build number. Binaries and sources of this build will be downloaded from the
[repository](https://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html) and used in 
compilation and testing. You can find build number of your IntelliJ product in `Help -> About` dialog. However, it might be 
incomplete, so it is strongly recommended to verify it against 
[available releases](https://www.jetbrains.com/intellij-repository/releases) and
[available snapshots](https://www.jetbrains.com/intellij-repository/snapshots).

**Note**: minimum supported major IDEA version: `242.x` (~`2024.2.x`)

#### `intellijPlatform in ThisBuild :: SettingKey[IntelliJPlatform]`

**Default**: `IntelliJPlatform.IdeaCommunity`

Edition of IntelliJ IDE to use in project. Currently available options are:

- IdeaCommunity
- IdeaUltimate
- PyCharmCommunity
- PyCharmProfessional
- CLion
- MPS

#### `intellijPlugins :: SettingKey[IdeaPlugin]`

**Default**: `Seq.empty`

IntelliJ plugins to depend on. Bundled(internal) plugins are specified by their plugin ID.
Plugins from repo can be specified by the plugin's id, optional version and update channel.
Plugins will be checked for compatibility against the `intellijBuild` you specified and updated to the latest version unless
 some specific version is given explicitly. Inter-plugin dependencies are also transitively resolved(e.g. depending
 on the Scala plugin will automatically attach Java and other plugin dependencies)
 
 Plugin IDs can be either searched by plugin name with the help of [searchPluginId](#searchpluginid--mapstring-string-boolean)
 task or [manually](https://github.com/JetBrains/sbt-idea-plugin/wiki/How-to-find-plugin's-id)
 
 You can tune plugin resolving on individual plugin level by specifying several options to `toPlugin` method:
 - `transitive`   - use transitive plugin resolution(default: true)
 - `optionalDeps` - resolve optional plugin dependencies(default: true)
 - `excludedIds`  - blacklist certain plugins from transitive resolution(default: Set.empty)
 
 :exclamation: Please note that Java support in IJ is implemented by a plugin: `com.intellij.java`
 
 :exclamation: Please remember that you must [declare plugin dependencies in plugin.xml](https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_dependencies.html#dependency-declaration-in-pluginxml) or your plugin may fail to load.

```SBT
// use properties plugin bundled with IDEA
intellijPlugins += "com.intellij.properties".toPlugin
// use Scala plugin as a dependency
intellijPlugins += "org.intellij.scala".toPlugin
// use Scala plugin version 2023.3.10
intellijPlugins += "org.intellij.scala:2023.3.10".toPlugin
// use latest nightly build from the repo
intellijPlugins += "org.intellij.scala::Nightly".toPlugin
// use specific version from Eap update channel
intellijPlugins += "org.intellij.scala:2023.3.10:Eap".toPlugin
// add JavaScript plugin but without its Grazie plugin dependency
intellijPlugins += "JavaScript".toPlugin(excludedIds = Set("tanvd.grazi"))
// add custom plugin with id `org.custom.plugin`, download it using the direct link https://org.example/path/to/your/plugin.zip
intellijPlugins += "org.custom.plugin:https://org.example/path/to/your/plugin.zip".toPlugin
// add custom plugin with id `org.custom.plugin` and resolve it from Marketplace.
//  if it fails to resolve it in Marketplace it will use the fallback download link
intellijPlugins += "org.custom.plugin".toPlugin.withFallbackDownloadUrl("https://org.example/path/to/your/plugin.zip")
```

#### `intellijRuntimePlugins :: SettingKey[IdeaPlugin]`

**Default**: `Seq.empty`

IntelliJ plugins to load at runtime (includes tests). These plugins are not a compile time dependencies and cannot be
referenced in code. Useful for testing your plugin in the presence of other plugins.

The usage is the same as `intellijPlugins`.

#### `searchPluginId :: Map[String, (String, Boolean)]`

Usage: `searchPluginId [--nobundled|--noremote] <plugin name regexp>`

Searches and prints plugins across locally installed IJ sdk and plugin marketplace.
Use provided flags to limit search scope to only bundled or marketplace plugins.

```
> searchPluginId Prop
[info] bundled          - Properties[com.intellij.properties]
[info] bundled          - Resource Bundle Editor[com.intellij.properties.bundle.editor]
```

#### `jbrInfo :: Option[JbrInfo]`

**Default**: `AutoJbr()`

JetBrains Java runtime version to use when running the IDE with the plugin. By default JBR version is extracted from
IDE installation metadata. Only jbr 11 is supported. Available versions can be found on [jbr bintray](https://github.com/JetBrains/JetBrainsRuntime/releases).
To disable, set to `NoJbr`

#### `patchPluginXml :: SettingKey[pluginXmlOptions]`

**Default**: `pluginXmlOptions.DISABLED`

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

Fine tune java VM options for running the plugin with [runIDE](#runide-nodebug-suspend-blocking--inputkeyunit) task.
Example:

```SBT
intellijVMOptions := intellijVMOptions.value.copy(xmx = 2048, xms = 256) 
```

#### `ideaConfigOptions :: SettingKey[IdeaConfigBuildingOptions]`

Fine tune how IntelliJ run configurations are generated when importing the project in IDEA.

#### `runIDE [noDebug] [suspend] [blocking] :: InputKey[Unit]`

Runs IntelliJ IDE with current plugin. This task is non-blocking by default, so you can continue using SBT console.

By default, IDE is run with non-suspending debug agent on port `5005`. This can be overridden by either optional
arguments above, or by modifying default [`intellijVMOptions`](#intellijvmoptions--settingkeyintellijvmoptions).

### Publishing and Verification

#### `publishPlugin [channel] :: InputKey[String]`

Upload and publish your IntelliJ plugin on https://plugins.jetbrains.com.
In order to publish to the repo you need to 
[obtain permanent token](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/api/plugin_upload.html) and
either place it into `~/.ij-plugin-repo-token` file or pass via `IJ_PLUGIN_REPO_TOKEN` env or java property.

This task also expects an optional argument - a [custom release channel](http://www.jetbrains.org/intellij/sdk/docs/plugin_repository/custom_channels.html).
If omitted, plugin will be published to the default plugin repository channel (Stable) 
 

#### `runPluginVerifier :: TaskKey[File]`

[IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) integration task allows to 
check the binary compatibility of the built plugin against the currently used or explicitly specified 
IntelliJ IDE builds. The task returns a folder with the verification reports.

The verification can be customized by changing the default options defined in the `pluginVerifierOptions` key.
```SBT
pluginVerifierOptions := pluginVerifierOptions.value.copy(
  version = "1.254",        // use a specific verifier version
  offline = true,           // forbid the verifier from reaching the internet
  overrideIDEs  = Seq("IC-2019.3.5", "PS-2019.3.2"), // verify against specific products instead of 'intellijBuild'
  failureLevels = Set(FailureLevel.DEPRECATED_API_USAGES) // only fail if deprecated APIs are used
   // ...
)
```

#### `signPlugin :: TaskKey[File]`

Utility task that signs the plugin artifact before uploading to the [JetBrains Marketplace](https://plugins.jetbrains.com/marketplace).
Signing is performed using the [Marketplace zip signer](https://github.com/JetBrains/marketplace-zip-signer)
library. To sign a plugin a valid certificate chain, and a private key are required.

Signing is disabled by default at the moment. To enable it and set the options, modify the `signPluginOptions` key:
```SBT
signPluginOptions := signPluginOptions.value.copy(
  enabled = true,
  certFile = Some(file("/path/to/certificate")), // or via PLUGIN_SIGN_KEY env var
  privateKeyFile  = Some(file("/path/to/privateKey")), // or via PLUGIN_SIGN_CERT env var
  keyPassphrase = Some("keyPassword") // or None if password is not set(or via PLUGIN_SIGN_KEY_PWD env var)
)
```

If signing the plugin artifact zip is enabled via `signPluginOptions`, this task will be used a dependency of the
[`publishPlugin`](#publishplugin-channel--inputkeystring) task, so that the artifact is automatically signed before 
uploading to the [JetBrains Marketplace](https://plugins.jetbrains.com/marketplace)

#### `buildIntellijOptionsIndex :: TaskKey[Unit]`

Builds index of options provided by the plugin to make them searchable via 
[search everywhere](https://www.jetbrains.com/help/idea/searching-everywhere.html#search_settings) action.
This task should either be manually called instead of `packageArtifact` or before `packageArtifactZip` since it patches 
jars already built by `packageArtifact`.

### Packaging

#### `packageMethod :: SettingKey[PackagingMethod]`

**Default for root project**: `PackagingMethod.Standalone(targetPath = s"lib/${name.value}.jar")`

**Default for all other subprojects**: `PackagingMethod.MergeIntoParent()`

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

**Default for root project**: `Seq.empty`

**Default for all other project**s:
```SBT
"org.scala-lang"  % "scala-.*" % ".*"        -> None ::
"org.scala-lang.modules" % "scala-.*" % ".*" -> None :: Nil
```

Sequence of rules to fine-tune how the library dependencies are packaged. By default all dependencies
including transitive are placed in the subfolder defined by 
[`packageLibraryBaseDir`](#packagelibrarybasedir--settingkeyfile)(defaults to "lib") of the plugin artifact.

You can use the [`findLibraryMapping`](#findlibrarymapping--inputkeyseqstring-seqmodulekey-optionstring)
task to debug the library mappings

```SBT
// merge all scalameta jars into a single jar
packageLibraryMappings += "org.scalameta" %% ".*" % ".*" -> Some("lib/scalameta.jar")

// skip packaging protobuf
packageLibraryMappings += "com.google.protobuf" % "protobuf-java" % ".*" -> None

// rename scala library(strip version suffix)
packageLibraryMappings += "org.scala-lang" % "scala-library" % scalaVersion -> Some("lib/scala-library.jar")
```

#### `packageLibraryBaseDir :: SettingKey[File]`

**Default**: `file("lib")`

Sets the per-project default sub-folder into which external libraries are packaged. Rules from [`packageLibraryMappings`](#packagefilemappings--taskkeyseqfile-string)
will override this setting. 

**NB!**: This directory must be relative to the [`packageOutputDir`](#packageoutputdir--settingkeyfile) so don't prepend
values of the keys with absolute paths (such as `target` or `baseDirectory`) to it

**NB!**: IDEA plugin classloader **only** adds the `lib` folder to the classpath when loading your plugin. Modifying 
this setting will essentially exclude the libraries of a project from automatic classloading

```SBT
packageLibraryBaseDir  := file("lib") / "third-party"

// protobuf will still be packaged into lib/protobuf.jar
packageLibraryMappings += "com.google.protobuf" % "protobuf-java" % ".*" -> Some("lib/protobuf.jar")
```

#### `packageFileMappings :: TaskKey[Seq[(File, String)]]`

**Default**: `Seq.empty`

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

**Default**: `Seq.empty`

By default the plugin builds artifact structure based on internal classpath dependencies
of the projects in an SBT build(`dependsOn(...)`). However, sometimes one may need to package
a project that no other depends upon. This setting is used to explicitly tell the plugin which 
projects to package into the artifact without a need to introduce unwanted classpath dependency.

#### `shadePatterns :: SettingKey[Seq[ShadePattern]]`

**Default**: `Seq.empty`

Class shading patterns to be applied by JarJar library. Used to resolve name clashes with 
libraries from IntelliJ platform such as protobuf.

```SBT
shadePatterns += ShadePattern("com.google.protobuf.**", "zinc.protobuf.@1")
```

#### `bundleScalaLibrary in ThisBuild :: SettingKey[Boolean]`

Trying to load the same classes in your plugin's classloader which have already been loaded by a parent classloader
will result in classloader constraint violation. A vivid example of this scenario is depending on some other plugin,
that bundles scala-library.jar(e.g. Scala plugin for IJ) and still bundling your own.

To workaround this issue `sbt-idea-plugin` tries to automatically detect if your plugin project has dependencies on 
other plugins with Scala and filter out scala-library.jar from the resulting artifact. However, the heuristic cannot
cover all possible cases and thereby this setting is exposed to allow manual control over bundling the scala-library.jar  

#### `instrumentThreadingAnnotations :: SettingKey[Boolean]`

**Default**: `false`

Generate JVM bytecode to assert that a method is called on the correct IDEA thread. The supported annotations are:
1. `com.intellij.util.concurrency.annotations.RequiresBackgroundThread`
2. `com.intellij.util.concurrency.annotations.RequiresEdt`
3. `com.intellij.util.concurrency.annotations.RequiresReadLock`
4. `com.intellij.util.concurrency.annotations.RequiresReadLockAbsence`
5. `com.intellij.util.concurrency.annotations.RequiresWriteLock`

See: [IntelliJ IDEA ThreadingAssertions.java](https://github.com/JetBrains/intellij-community/blob/5758eb99b4a1971ebe75cda755693cc930949465/platform/core-api/src/com/intellij/util/concurrency/ThreadingAssertions.java)

#### `packageOutputDir :: SettingKey[File]`

**Default**: `target.value / "plugin" / intellijPluginName.in(ThisBuild).value.removeSpaces`

Folder to place the assembled artifact into.

#### `packageArtifact :: TaskKey[File]`

Builds unpacked plugin distribution. This task traverses dependency graph of the
build and uses settings described in the section above to create sub-artifact structure 
for each project. By default all child projects' classes are merged into the root project jar,
which is placed into the "lib" folder of the plugin artifact, all library dependencies
including transitive are placed in the "lib" folder as well. 
 
 
#### `packageArtifactZip :: TaskKey[File]`

Produces ZIP file from the artifact produced by `packageArtifact` task.
This is later used by `publishPlugin` as an artifact to upload.

### Utils

#### `findLibraryMapping :: InputKey[Seq[(String, Seq[(ModuleKey, Option[String])])]]`

Returns detailed info about libraries and their mappings by a library substring.
Helps to answer questions such as "Why is this jar in the artifact?" or "Which module introduced this jar?"
Example:
```
sbt:scalaUltimate> show findMapping interface
[info] * (runtimeDependencies,ArrayBuffer((org.scala-sbt:compiler-interface:1.4.0-M12[],Some(lib/jps/compiler-interface.jar)), (org.scala-sbt:util-interface:1.3.0[],Some(lib/jps/sbt-interface.jar))))
[info] * (repackagedZinc,ArrayBuffer((org.scala-sbt:compiler-interface:1.4.0-M12[],Some(*)), (org.scala-sbt:launcher-interface:1.1.3[],Some(*)), (org.scala-sbt:util-interface:1.3.0[],Some(*))))
[info] * (compiler-jps,ArrayBuffer((org.scala-sbt:util-interface:1.3.0[],Some(*)), (org.scala-sbt:compiler-interface:1.4.0-M12[],Some(lib/jps/compiler-interface.jar))))
[info] * (compiler-shared,ArrayBuffer((org.scala-sbt:util-interface:1.3.0[],Some(*)), (org.scala-sbt:compiler-interface:1.4.0-M12[],Some(*))))
```

#### `printProjectGraph :: TaskKey[Unit]`

Prints ASCII graph of currently selected project to console. Useful for debugging complex builds.
![](https://user-images.githubusercontent.com/1345782/65074597-d8172880-d99d-11e9-947e-aa74855e9ff1.png)

## Running the plugin

### From SBT

To run the plugin from SBT simply use [runIDE](#runide-nodebug-suspend-blocking--inputkeyunit) task.
Your plugin will be automatically compiled, an artifact built and attached to new IntelliJ instance.

Debugger can later be attached to the process remotely - the default port is 5005.

### From IDEA

- `sbt-idea-plugin` generates IDEA-readable artifact xml and run configuration on project import
- After artifact and run configuration have been created(they're located in `.idea` folder of the project) you can 
run or debug the new run configuration. This will compile the project, build the artifact and attach it to the
 new IDEA instance
- :exclamation: Note that doing an "SBT Refresh" is required after making changes to your build
that affect the final artifact(i.e. changing `libraryDependencies`), in order to update IDEA configs
- :exclamation: You may need to [manually build the artifact](#plugin-artifact-not-built-when-running-from-idea-after-importing-the-project)
  when running your plugin for the first time
  
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
## Grouping with qualified names is available from Scala plugin version 2024.1.4
In the Scala plugin 2024.1.4 a significant change has been made according to modules grouping and their naming. You can read more about this change [here](https://youtrack.jetbrains.com/issue/SCL-21288/Rewrite-deprecated-module-grouping-logic-to-the-new-API-grouping-with-qualified-names-is-now-supported.#focus=Comments-27-8977291.0-0). 
Because of this change, it was necessary to change how the project names are generated in packageMapping tasks (`packageMappings` and `packageMappingsOffline`).
To switch between the new and the old projects naming logic, `grouping.with.qualified.names.enabled` system property has been introduced.
If your Scala plugin version is 2024.1.4 or higher, then in order to generate correct mappings you should set this property to true (`-Dgrouping.with.qualified.names.enabled=true`). 
Otherwise, there is no need to do anything as this value is set to false by default.

## Known Issues and Limitations

### `name` key in projects

Please do not explicitly set the `name` setting key for projects that have `SbtIdeaPlugin` attached.
SBT will automatically set it from the `lazy val`'s name of the project definition.

IDEA cannot correctly handle the sutuation when `name` key and `lazy val`'s name of a project are different,
thus making the generated artifact and run configuration xml's invalid.

Related issue: https://github.com/JetBrains/sbt-idea-plugin/issues/72

### Plugin artifact not built when running from IDEA after importing the project

The generated IDEA run configurations depend on the built artifact of the plugin, so it should be built 
automatically when running or debugging the generated configuration.

However, when the IDEA xml configuration file is created externally, like in the case of `sbt-idea-plugin`,
it is sometimes not picked up immediately and requires an explicit loading.

It is recommended to explicitly invoke `Build | Build Artifacts | Rebuild` from IDEA after importing the project 
for the first time(i.e. when xmls are first generated).

### Development notes
To publish a new version of `sbt-idea-plugin`, just add a new tag in format `vA.B.C` (e.g.`v3.13.4`) and push it to the main branch.
TeamCity will automatically  Build/Test/Deploy it in `sbt-idea-plugin` configuration. \
(works in internal network only)