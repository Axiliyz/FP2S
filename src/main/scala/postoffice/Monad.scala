package postoffice

trait Monad[F[_]]:
  def pure[A](a: A): F[A]
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def map[A, B](fa: F[A])(f: A => B): F[B] = flatMap(fa)(a => pure(f(a)))
  def andThen[A, B](fa: F[A])(fb: F[B]): F[B] = flatMap(fa)(_ => fb)

object Monad:
  def apply[F[_]](using ev: Monad[F]): Monad[F] = ev

  extension [F[_], A](fa: F[A])(using m: Monad[F])
    def flatMap[B](f: A => F[B]): F[B] = m.flatMap(fa)(f)
    def map[B](f: A => B): F[B]         = m.map(fa)(f)
    def >>[B](fb: F[B]): F[B]           = m.andThen(fa)(fb)
