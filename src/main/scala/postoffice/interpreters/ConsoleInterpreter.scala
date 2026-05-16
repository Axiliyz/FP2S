package postoffice

import postoffice.algebras.ConsoleAlgebra

object ConsoleInterpreter extends ConsoleAlgebra[AppF]:
  def readLine: AppF[String] =
    s => (s, Option(scala.io.StdIn.readLine()).getOrElse(""))

  def putStr(msg: String): AppF[Unit] =
    s => {print(msg); (s, ())}

  def putStrLn(msg: String): AppF[Unit] =
    putStr(msg + "\n")