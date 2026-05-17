package postoffice

import postoffice.algebras.ConfigAlgebra

final class ConfigInterpreter(
  taxPerKg: Double,
  maxWeightKg: Double,
  storageRatePerDay: Double,
  weightClasses: List[(Double, PackageClass)],
  packagingRules: Map[PackageClass, PackagingRule]
) extends ConfigAlgebra[AppF]:
  def acceptanceCost(weightKg: Double): AppF[Double] =
    statePure(weightKg * taxPerKg)

  def canAccept(weightKg: Double): AppF[Boolean] =
    statePure(weightKg > 0 && weightKg <= maxWeightKg)

  def storageCost(days: Int): AppF[Double] =
    statePure(days * storageRatePerDay)

  def packageClass(weightKg: Double): AppF[(PackageClass, PackagingRule)] =
    val cls  = weightClasses.find((t, _) => weightKg <= t).map(_(1)).getOrElse(PackageClass.Heavy)
    val rule = packagingRules.getOrElse(cls, PackagingRule.Crate)
    statePure((cls, rule))