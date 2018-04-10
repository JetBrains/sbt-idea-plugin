# sbt-idea-plugin

[ ![Version](https://api.bintray.com/packages/sbt/sbt-plugin-releases/sbt-idea-plugin/images/download.svg) ](https://bintray.com/jetbrains/sbt-plugins/sbt-idea-plugin/_latestVersion) 
[![Build Status](https://travis-ci.org/jetbrains/sbt-idea-plugin.svg)](https://travis-ci.org/jetbrains/sbt-idea-plugin)
[![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

SBT plugin that makes development of IntelliJ IDEA plugins in Scala easier.

## Issues?

Please report issues at the [IntelliJ Scala YouTrack](https://youtrack.jetbrains.com/issues/SCL).

## Installation

From version 1.0.0, this plugin is published for sbt 0.13 and 1.0

* Insert into `project/plugins.sbt`:

```Scala
addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "1.0.1")
```

* Run SBT and execute `updateIdea` task. It will download IDEA and external plugins used in project

* Start coding

## Available settings

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

Default: `baseDirectory / "idea"`

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

#### `ideaPublishSettings :: SettingKey[PublishSettings]`

Default: `PublishSettings(pluginId = "", username = "", password = "", channel = None)`

Settings necessary for uploading your IDEA plugin to https://plugins.jetbrains.com

#### `ideaPluginFile :: TaskKey[File]`

Default: aliased to `package in Compile`

Your IDEA plugin file to publish on https://plugins.jetbrains.com

## Tasks

#### `updateIdea :: TaskKey[Unit]`

Download IDEA's binaries and sources, put them into
`ideaBaseDirectory` directory. Download external plugins and put
them in `ideaBaseDirectory / "externalPlugins"` directory. Automatically add IDEA's and
plugin's jars into `unmanagedJars in Compile`.

#### `publishPlugin :: TaskKey[String]`

Upload and publish your IDEA plugin on https://plugins.jetbrains.com. Returns
URL of published plugin.

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
  
