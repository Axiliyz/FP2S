package postoffice

import cats.data.StateT
import cats.effect.IO

type AppF[A] = StateT[IO, PostState, A]

given Monad[AppF] with
  def pure[A](a: A): AppF[A]                               = StateT.pure(a)
  def flatMap[A, B](fa: AppF[A])(f: A => AppF[B]): AppF[B] = fa.flatMap(f)
