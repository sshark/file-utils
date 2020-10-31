# file-utils

*file-utils* is a project to highlight the compilation of Scala code to a native application using GraavlVM `native-image`.
It is a simple application to calculate the disk space occupied by the files in the given top directory. 

## Setup

Run `sbt` followed by the command `native-image` to generate the native image for the target OS. However, the command might
not work out of the box because the build environment is not setup for GraalVM `native-image` yet. Please refer to the
[GraalVM documents](https://www.graalvm.org/reference-manual/native-image/) for more details.

This application is written using these functional style libraries: -
1. [cats-effect](https://github.com/typelevel/cats-effect) 
1. [log4cats](https://github.com/ChristopherDavenport/log4cats)
1. [console4cats](https://github.com/profunktor/console4cats)
1. [decline](http://ben.kirw.in/decline/)

The generated executable file is lcoated at `target\native-image\file-utils.exe`. Modify `name` in `build.sbt` to change 
the filename if necessary.

## Notes

Though `META-INF\native-image\reflect-config.json` is provided, sometimes it is necessary to generate new `native-image`
configuration files. Add `-agentlib:native-image-agent=config-output-dir=META-INF\native-image\` to the `java` command
line to generate new sets of configuration files.