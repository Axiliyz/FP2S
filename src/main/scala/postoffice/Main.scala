package postoffice

import cats.effect.{IO, IOApp}
import java.io.PrintStream

object Main extends IOApp.Simple:

  def run: IO[Unit] =
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    System.setErr(PrintStream(System.err, true, "UTF-8"))

    val alg = Interpreters.make(PostConfig.default)
    Program.menu[AppF](using alg.cfg, alg.state, alg.log, alg.console, alg.id)
      .runA(PostState.empty)
