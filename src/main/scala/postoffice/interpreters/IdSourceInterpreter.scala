package postoffice

import cats.data.StateT
import cats.effect.IO
import postoffice.algebras.IdSourceAlgebra

object IdSourceInterpreter extends IdSourceAlgebra[AppF]:
  def nextId: AppF[ParcelId] = StateT { s =>
    IO.pure((s.copy(nextId = s.nextId + 1), ParcelId(s.nextId)))
  }
