package postoffice

import postoffice.algebras.*

final case class Algebras[F[_]](
  cfg:     ConfigAlgebra[F],
  log:     LogAlgebra[F],
  state:   StateAlgebra[F],
  console: ConsoleAlgebra[F],
  id:      IdSourceAlgebra[F],
)

object Interpreters:
  def make(cfg: PostConfig): Algebras[AppF] =
    Algebras(
      cfg     = ConfigInterpreter(cfg),
      log     = LogInterpreter,
      state   = StateInterpreter,
      console = ConsoleInterpreter,
      id      = IdSourceInterpreter,
    )
