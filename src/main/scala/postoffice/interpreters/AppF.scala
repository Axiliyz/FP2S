package postoffice

type AppF[A] = PostState => (PostState, A)

def stateModify(f: PostState => PostState): AppF[Unit] =
  s => (f(s), ())
def stateInspect[A](f: PostState => A): AppF[A] =
  s => (s, f(s))
def statePure[A](a: A): AppF[A] =
  s => (s, a)

given Monad[AppF] with
  def pure[A](a: A): AppF[A] = statePure(a)
  def flatMap[A, B](fa: AppF[A])(f: A => AppF[B]): AppF[B] =
    s =>
      val (s1, a) = fa(s)
      f(a)(s1)