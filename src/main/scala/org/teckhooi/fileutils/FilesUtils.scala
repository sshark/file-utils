package org.teckhooi.fileutils

import cats.effect.{IO, Sync}

import java.nio.file.{Files, Path}

trait FilesUtils[F[_]] {
  def size(filename: String): F[Long]
  def fullPath(filename: String): F[String]
}

object FilesUtils {
  def apply[F[_] : FilesUtils]: FilesUtils[F] = implicitly[FilesUtils[F]]

  object implicits {
    implicit def ioFilesUtils: DefaultFilesUtils[IO] = new DefaultFilesUtils[IO]
  }
}

class DefaultFilesUtils[F[_]: Sync] extends FilesUtils[F] {
  override def size(filename: String): F[Long] = Sync[F].delay(Files.size(Path.of(filename)))
  override def fullPath(filename: String): F[String] = Sync[F].delay(Path.of(filename).toFile.getAbsolutePath)
}
