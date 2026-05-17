package postoffice

import postoffice.algebras.IdSourceAlgebra

object IdSourceInterpreter extends IdSourceAlgebra[AppF]:
  def nextId: AppF[Int] =
  s => (s.copy(nextId = s.nextId + 1), s.nextId)
