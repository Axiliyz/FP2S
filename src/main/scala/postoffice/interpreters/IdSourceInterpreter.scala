package postoffice

import postoffice.algebras.IdSourceAlgebra

object IdSourceInterpreter extends IdSourceAlgebra[AppF]:
  def nextId: AppF[ParcelId] =
  s => (s.copy(nextId = s.nextId + 1), ParcelId(s.nextId))
