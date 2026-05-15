package monads

// Reader[Env, A] оборачивает функцию Env => A.
// Моделирует вычисление, которому нужна среда Env (конфиг) для получения результата A.
// Среда read-only: читать можно, изменять нельзя.
final case class Reader[Env, A](run: Env => A):

  // Применяет f к результату, среда остаётся той же самой.
  def map[B](f: A => B): Reader[Env, B] =
    Reader(env => f(run(env)))

  // Ключевой метод: одна и та же env передаётся ОБОИМ вычислениям.
  // run(env) даёт A, f(A) даёт следующий Reader, его тоже запускаем с той же env.
  // Это и есть смысл Reader — среда протаскивается автоматически через всю цепочку.
  def flatMap[B](f: A => Reader[Env, B]): Reader[Env, B] =
    Reader(env => f(run(env)).run(env))

object Reader:

  // Возвращает Reader, чьим значением является само окружение целиком.
  // identity = x => x. Используется когда нужна вся среда, не её часть.
  def ask[Env]: Reader[Env, Env] = Reader(identity)

  // Извлекает конкретное поле из окружения через функцию f.
  // Основной способ создания Reader в проекте — ReaderOps написан через asks.
  def asks[Env, A](f: Env => A): Reader[Env, A] = Reader(f)

  // Нейтральный элемент: _ означает "окружение игнорируется".
  // "Вычисление без зависимости от среды".
  def pure[Env, A](a: A): Reader[Env, A] = Reader(_ => a)

  // given — свидетель того, что Reader[Env, ?] является монадой.
  // [A] =>> Reader[Env, A] — лямбда на уровне типов: фиксирует Env, оставляет A свободным.
  // Нужна потому что Monad ожидает M[_] (один параметр), а Reader имеет два: [Env, A].
  given readerMonad[Env]: Monad[[A] =>> Reader[Env, A]] with
    def pure[A](a: A): Reader[Env, A] = Reader.pure(a)
    def flatMap[A, B](ma: Reader[Env, A])(f: A => Reader[Env, B]): Reader[Env, B] =
      ma.flatMap(f)
