package postoffice

import cats.effect.{IO, IOApp}
import java.io.PrintStream

object Main extends IOApp.Simple:

  def run: IO[Unit] =
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    System.setErr(PrintStream(System.err, true, "UTF-8"))

    val ioAlg = Interpreters.makeIO(PostConfig.default)
    Program.menu[AppF](using ioAlg.cfg, ioAlg.state, ioAlg.log, ioAlg.console)
      .runA(PostState.empty)
