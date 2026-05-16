package postoffice

import cats.data.StateT
import postoffice.algebras.ConfigAlgebra

final class ConfigInterpreter(cfg: PostConfig) extends ConfigAlgebra[AppF]:
  def acceptanceCost(weightKg: Double): AppF[Double] = StateT.pure(weightKg * cfg.taxPerKg)
  def canAccept(weightKg: Double): AppF[Boolean]     = StateT.pure(weightKg > 0 && weightKg <= cfg.maxWeightKg)
  def storageCost(days: Int): AppF[Double]           = StateT.pure(days * cfg.storageRatePerDay)
  def packageClass(weightKg: Double): AppF[(PackageClass, PackagingRule)] = StateT.pure {
    val cls  = cfg.weightClasses.find((t, _) => weightKg <= t).map(_(1)).getOrElse(PackageClass.Heavy)
    val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
    (cls, rule)
  }
