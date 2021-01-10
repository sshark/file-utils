package org.teckhooi.fileutils

import cats.effect.{IO, Sync}

import java.io.File
import java.nio.file.{Files, Path}

trait FilesUtils[F[_]] {
  def size(filename: String): F[Long]
  def fullPath(filename: String): F[String]
}

trait FilesUtilsSyncRequired[F[_]] {
  def size(path: Path): F[Long]
  def fullPath(filename: File): F[String]
}

object FilesUtilsSyncRequired {
  def apply[F[_]: FilesUtilsSyncRequired]: FilesUtilsSyncRequired[F] = implicitly[FilesUtilsSyncRequired[F]]

  object implicits {
    implicit def ioFilesUtils: FilesUtilsSyncRequired[IO] = new SyncFilesUtils[IO]
  }
}

class SyncFilesUtils[F[_]: Sync] extends FilesUtilsSyncRequired[F] {
  override def size(path: Path): F[Long]       = Sync[F].delay(Files.size(path))
  override def fullPath(file: File): F[String] = Sync[F].delay(file.getAbsolutePath)
}
