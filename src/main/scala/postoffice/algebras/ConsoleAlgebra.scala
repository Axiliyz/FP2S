package postoffice.algebras

trait ConsoleAlgebra[F[_]]:
  def readLine: F[String]
  def putStrLn(s: String): F[Unit]
  def putStr(s: String): F[Unit]
