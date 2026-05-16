package postoffice

import postoffice.algebras.ConfigAlgebra

final class ConfigInterpreter(cfg: PostConfig) extends ConfigAlgebra[AppF]:
  def acceptanceCost(weightKg: Double): AppF[Double] =
    statePure(weightKg * cfg.taxPerKg)

  def canAccept(weightKg: Double): AppF[Boolean] =
    statePure(weightKg > 0 && weightKg <= cfg.maxWeightKg)

  def storageCost(days: Int): AppF[Double] =
    statePure(days * cfg.storageRatePerDay)

  def packageClass(weightKg: Double): AppF[(PackageClass, PackagingRule)] =
    val cls  = cfg.weightClasses.find((t, _) => weightKg <= t).map(_(1)).getOrElse(PackageClass.Heavy)
    val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
    statePure((cls, rule))