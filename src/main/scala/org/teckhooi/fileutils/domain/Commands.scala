package org.teckhooi.fileutils.domain

import java.nio.file.Path

sealed trait FileCommand
case class ListCommand(pathStr: Path) extends FileCommand
case class DeleteCommand(pathStr: Path, deletePattern: List[String]) extends FileCommand
