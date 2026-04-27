package postoffice.algebras

// Tagless Final — «с нестабильным» (volatile/IO): ввод-вывод с консолью
// по природе нестабилен (побочный эффект). F здесь обязательно
// должен поддерживать эффекты (IO), поэтому консоль изолирована
// в отдельную алгебру — бизнес-логика не смешивается с IO.
trait ConsoleAlgebra[F[_]]:
  def readLine: F[String]
  def putStrLn(s: String): F[Unit]
  def putStr(s: String): F[Unit]
