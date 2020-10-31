# file-utils

*file-utils* is a project to demonstrate the transformation of a functional Scala code (consists of pure functions only) to a native application using GraavlVM `native-image`. Side effects are contained and pushed to the fringe of the application where they will be executed at the *end of the world*.

The purpose of this application is to calculate the disk space occupied by the files for the given top directory.

## Setup

Run `sbt` followed by the command `nativeImage` to generate the native image for the target OS. However,
the command might not work out of the box because the build environment is not setup for GraalVM
`native-image`. Please refer to the [GraalVM documents](https://www.graalvm.org/reference-manual/native-image/) for more details.

This application is written using these functional style libraries: -
1. [cats-effect](https://github.com/typelevel/cats-effect) 
1. [log4cats](https://github.com/ChristopherDavenport/log4cats)
1. [console4cats](https://github.com/profunktor/console4cats)
1. [decline](http://ben.kirw.in/decline/)

`nativeImage` will generate an executable file and place it at `target\native-image\file-utils.exe`.
Modify `name` in `build.sbt` to change the filename if necessary.

## Notes

Though `META-INF\native-image\reflect-config.json` is provided, sometimes it is necessary to have a
new set of `native-image` configuration files. Add `-agentlib:native-image-agent=config-output-dir=META-INF\native-image\` 
to the `java` command line to generate a new set of configuration files.
