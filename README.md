# sbt-idea-plugin

SBT plugin for developers writing plugins for Intellij IDEA in Scala and Java. WIP.

## Usage

* Insert into `project/plugins.sbt`:

```Scala
resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % "0.1.0")
```

* Insert into `build.sbt`:

```Scala
ideaPluginSettings

ideaBuild := "139.1117.1" // See notes below about IDEA builds
```

* Download IDEA binaries and sources by running `updateIdea` command in SBT REPL

* Start coding

## Settings

#### ideaBuild

No default value

It should contain build number of IDEA executable which will be used for
compiling and testing project. Corresponding binaries and sources will be
downloaded from https://teamcity.jetbrains.com. You can find build number of
your IDEA's executable in "About" dialog. However, it might be incomplete, so I
strongly recommend you to verify it
[here](https://teamcity.jetbrains.com/viewType.html?buildTypeId=bt410&tab=buildTypeStatusDiv&branch_IntelliJIdeaCe=__all_branches__)

#### ideaBaseDirectory

Default: `baseDirectory / "idea"`

Contains path to a directory where IDEA binaries and sources will be unpacked.

#### ideaPlugins

Default: `Seq.empty`

List of IDEA plugins to depend on. Their jars will be used in compilation.
Available plugins can be found in `ideaBaseDirectory/plugins` directory.

## Tasks

#### updateIdea

Downloads IDEA's binaries and sources, put them into
`ideaBaseDirectory/ideaBuild` directory and automatically add into
`unmanagedJars`.

## Notes

- If you use `sbt-assembly` plugin to produce a fat jar to
  distribute your plugin you should avoid putting IDEA's jars
  into this fat jar of yours. To achieve this insert

  ```Scala
  assemblyExcludedJars in aassembly <<= ideaFullJars
  ```

  into your `build.sbt`
