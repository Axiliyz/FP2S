package monads

// IO[A] — ОПИСАНИЕ вычисления, которое может иметь побочные эффекты и вернёт A.
// Не само вычисление, а инструкция к нему. Поле unsafeRun: () => A не вызывается
// до явной команды. Называется unsafe потому что при вызове могут быть побочные эффекты.
final case class IO[A](unsafeRun: () => A):

  // Создаёт новый IO: при запуске выполнит this, затем применит f.
  // Ничего не происходит до явного unsafeRun().
  def map[B](f: A => B): IO[B] =
    IO(() => f(unsafeRun()))

  // Создаёт новый IO: выполнит this, передаст результат в f,
  // получит следующий IO, запустит и его.
  // Весь for-comprehension строит одно большое вложенное описание —
  // ничего не выполняется до unsafeRun() в main.
  def flatMap[B](f: A => IO[B]): IO[B] =
    IO(() => f(unsafeRun()).unsafeRun())

object IO:

  // Нейтральный IO: лямбда возвращает a немедленно без побочных эффектов.
  def pure[A](a: A): IO[A] = IO(() => a)

  // a: => A — call-by-name: a не вычисляется при передаче.
  // IO.delay(println("hi")) не печатает ничего при создании — только при unsafeRun.
  // Разница с pure: pure ожидает готовое значение, delay откладывает вычисление.
  def delay[A](a: => A): IO[A] = IO(() => a)

  // Оборачивает println в IO. Побочный эффект описан, но не выполнен.
  def putStrLn(s: String): IO[Unit] = IO(() => println(s))

  // Оборачивает чтение строки. Option(...) защищает от null —
  // readLine() возвращает null при EOF. getOrElse("") — вернуть пустую строку.
  val getStrLn: IO[String] = IO(() => Option(scala.io.StdIn.readLine()).getOrElse(""))

  // given — свидетель что IO является монадой.
  // IO имеет только один параметр типа — лямбда на уровне типов не нужна,
  // в отличие от Reader[Env, ?] и State[S, ?].
  given ioMonad: Monad[IO] with
    def pure[A](a: A): IO[A] = IO.pure(a)
    def flatMap[A, B](ma: IO[A])(f: A => IO[B]): IO[B] = ma.flatMap(f)
