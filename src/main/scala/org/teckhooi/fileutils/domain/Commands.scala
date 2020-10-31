package org.teckhooi.fileutils.domain

sealed trait FileCommand
case class ListCommand(pathStr: String) extends FileCommand
case class DeleteCommand(pathStr: String, deletePattern: List[String]) extends FileCommand
