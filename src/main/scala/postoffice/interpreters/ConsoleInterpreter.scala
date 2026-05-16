package postoffice

import cats.data.StateT
import cats.effect.IO
import postoffice.algebras.ConsoleAlgebra

object ConsoleInterpreter extends ConsoleAlgebra[AppF]:
  def readLine: AppF[String]          = StateT.liftF(IO(Option(scala.io.StdIn.readLine()).getOrElse("")))
  def putStr(s: String): AppF[Unit]   = StateT.liftF(IO(print(s)))
  def putStrLn(s: String): AppF[Unit] = putStr(s + "\n")
