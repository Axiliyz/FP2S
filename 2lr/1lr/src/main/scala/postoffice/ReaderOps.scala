package postoffice

import monads.Reader

// Набор вычислений, зависящих от PostOfficeConfig.
// Каждая функция возвращает Reader — описание зависимости, а не готовый результат.
// Конфиг не передаётся явно внутри — он придёт снаружи при вызове .run(cfg).
object ReaderOps:

  // Стоимость приёма посылки: вес * тариф из конфига.
  // Reader.asks создаёт Reader из функции Env => A.
  // weight захватывается из замыкания, cfg.taxPerKg — из конфига при .run(cfg).
  def acceptanceCost(weight: Double): Reader[PostOfficeConfig, Double] =
    Reader.asks(cfg => weight * cfg.taxPerKg)

  // Проверяет, можно ли принять посылку.
  // weight > 0 — не нулевой вес. weight <= cfg.maxWeight — не превышает максимум из конфига.
  def canAccept(weight: Double): Reader[PostOfficeConfig, Boolean] =
    Reader.asks(cfg => weight > 0 && weight <= cfg.maxWeight)

  // Стоимость хранения: количество дней * стоимость из конфига.
  // days вычисляется снаружи из State и передаётся как параметр.
  def storageCost(days: Int): Reader[PostOfficeConfig, Double] =
    Reader.asks(cfg => days * cfg.storageFeePerDay)

  // Определяет класс посылки и правило упаковки по весу.
  // Возвращает пару (PackageClass, PackagingRule).
  def packageClass(weight: Double): Reader[PostOfficeConfig, (PackageClass, PackagingRule)] =
    Reader.asks { cfg =>
      // find ищет первый порог >= веса в weightClasses (список проверяется по порядку).
      // _(1) — берёт второй элемент пары (сам PackageClass).
      // getOrElse(Heavy) — если вес превышает все пороги, класс Heavy.
      val cls = cfg.weightClasses
        .find((threshold, _) => weight <= threshold)
        .map(_(1))
        .getOrElse(PackageClass.Heavy)
      // По классу находим правило упаковки. getOrElse(Crate) — fallback.
      val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
      (cls, rule)
    }
