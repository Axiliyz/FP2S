package postoffice

import postoffice.algebras.*

final case class Algebras[F[_]](
  cfg:     ConfigAlgebra[F],
)

object Interpreters:
  def make(cfg: PostConfig): Algebras[AppF] =
    Algebras(
      cfg     = ConfigInterpreter(cfg)
    )
