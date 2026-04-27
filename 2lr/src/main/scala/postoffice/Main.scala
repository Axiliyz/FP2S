package postoffice

import cats.effect.{IO, IOApp}
import postoffice.algebras.*
import java.io.PrintStream

object Main extends IOApp.Simple:

  def run: IO[Unit] =
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    System.setErr(PrintStream(System.err, true, "UTF-8"))
    Interpreters.make(PostConfig.default).flatMap { algebras =>
      given ConfigAlgebra[IO]  = algebras.cfg
      given LogAlgebra[IO]     = algebras.log
      given StateAlgebra[IO]   = algebras.state
      given ConsoleAlgebra[IO] = algebras.console
      Program.menu[IO]
    }
