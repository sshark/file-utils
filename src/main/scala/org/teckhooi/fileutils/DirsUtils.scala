package org.teckhooi.fileutils

import java.io.File

import cats.Parallel
import cats.effect.{ContextShift, IO, Sync}
import cats.implicits._
import ch.qos.logback.classic.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

trait DirsUtils[F[_]] {
  def dirSize(rootDir: String, verbose: Boolean = false): F[Long]
  def rm[A](rootDir: String, patterns: List[String], verbose: Boolean = false): F[Boolean]
  def dirExists(dir: String): F[Option[String]]
}

object DirsUtils {
  def apply[F[_]](implicit F: DirsUtils[F]): DirsUtils[F] = F

  object implicits {
    import FilesUtils.implicits._

    implicit def ioDirsUtils(implicit cs: ContextShift[IO]): DirsUtils[IO] = new DefaultDirsUtils[IO]
  }
}

class DefaultDirsUtils[F[_]: Sync: Parallel: FilesUtils] extends DirsUtils[F] {
  override def dirExists(dir: String): F[Option[String]] =
    for {
      rootDir <- Sync[F].delay(new File(dir))
      exists  <- Sync[F].delay(rootDir.exists())
    } yield Option.when(exists)(dir)

  override def dirSize(rootDir: String, verbose: Boolean): F[Long] =
    for {
      _ <- if (verbose)
        Sync[F].delay(
          org.slf4j.LoggerFactory
            .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            .asInstanceOf[Logger]
            .setLevel(ch.qos.logback.classic.Level.DEBUG))
      else Sync[F].unit
      logger               <- Slf4jLogger.create[F]
      (totalSize, subDirs) <- Sync[F].delay(new File(rootDir)).flatMap(findDirsAndSize)
      fullPath             <- FilesUtils[F].fullPath(rootDir)
      _                    <- logger.debug(s"$fullPath => ${prettyPrint(totalSize)} bytes")
      sum                  <- subDirs.parTraverse(oneDir => dirSize(oneDir.getCanonicalPath)).map(_.sum + totalSize)
    } yield sum

  private def findDirsAndSize(baseDir: File): F[(Long, List[File])] =
    Sync[F].delay(
      Option(baseDir.listFiles())
        .map { xs =>
          xs.map(f => if (f.isFile) f.length.asLeft[File] else f.asRight[Long]).foldLeft((0L, List.empty[File])) {
            case ((fs, xs), x) => x.fold(newLen => (newLen + fs, xs), newFile => (fs, newFile :: xs))
          }
        }
        .getOrElse((0, Nil)))

  override def rm[A](rootDir: String, patterns: List[String], verbose: Boolean): F[Boolean] = Sync[F].pure(true)
}
