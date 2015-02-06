# sbt-idea-plugin

SBT plugin for developers writing plugins for Intellij IDEA in Scala and Java. WIP.

# Usage

* Insert into `project/plugins.sbt`:

```Scala
resolvers += Resolver.url("dancingrobot84-bintray",
  url("http://dl.bintray.com/dancingrobot84/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.dancingrobot84" % "sbt-idea-plugin" % 
  "0.0-73ee17a3aeed5052eb103a11849125e65ce59d91")
```

* Insert into `build.sbt`:

```Scala
ideaPluginSettings

ideaVersion := "14.0.3" // Put here version of IDEA your plugin depends on
```

* Download IDEA binaries and sources by running `updateIdea` command in SBT REPL

* Start coding
