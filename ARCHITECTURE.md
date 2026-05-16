# Post Office Tagless Final Architecture

## 📋 ОБЗОР ПРОЕКТА

Это симуляция почтового отделения на **Scala 3** с использованием **Tagless Final pattern**. 

**Главная идея**: код разделён на две части:
- **Что система делает** (algebras/traits) — описание операций
- **Как система это делает** (interpreters) — реализация операций

Благодаря этому одна программа работает с разными реализациями. Меняешь интерпретатор → меняется поведение, программа остаётся неизменной.

---

## 🏗️ АРХИТЕКТУРА: 5 СЛОЁВ

**Диаграмма взаимодействия слоёв:**

```
┌─────────────────────────────────────────────────────────────┐
│                    MAIN (точка входа)                       │
│              Создаёт интерпретаторы и запускает             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              PROGRAM (бизнес-логика)                        │
│  acceptFlow, pickupFlow, nextDayFlow, summaryFlow           │
│  Использует algebras через using, не знает о AppF           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                 MENU (навигация)                            │
│           MenuTreeNode, MenuLeaf, userInteractionLoop       │
│                  Реализует UI логику                        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌────────────────┬─────────────┬──────────┬────────┬──────────┐
│ ConfigAlgebra  │StateAlgebra │ConsoleA. │LogAlg. │IdSourceA.│
│  (Algebra)     │  (Algebra)  │(Algebra) │(Alg.)  │(Algebra) │
└────────────────┴─────────────┴──────────┴────────┴──────────┘
         ↓              ↓            ↓           ↓           ↓
┌────────────────┬─────────────┬──────────┬────────┬──────────┐
│ConfigInterpreter│StateInterp. │ConsoleI. │LogInterpIdsourceI│
│  (Interpreter) │ (Interpreter)│(Interp.) │(Interp)│(Interp.) │
└────────────────┴─────────────┴──────────┴────────┴──────────┘
         ↓              ↓            ↓           ↓           ↓
┌─────────────────────────────────────────────────────────────┐
│                AppF[A] (State Monad)                        │
│         PostState => (PostState, A)                         │
└─────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────┐
│              Monad[AppF] (типовой класс)                    │
│          pure, flatMap, map, andThen, >>                    │
└─────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────┐
│                  DOMAIN (типы данных)                       │
│  PostState, Parcel, PostConfig, PackageClass, etc.          │
└─────────────────────────────────────────────────────────────┘
```

### 1️⃣ DOMAIN СЛОЙ (Domain.scala)
**Назначение**: определяет все типы данных в системе.

```scala
// Правила упаковки
enum PackagingRule(val description: String):
  case Envelope extends PackagingRule("envelope")
  case Box extends PackagingRule("box")
  case Crate extends PackagingRule("crate")

// Классы посылок по весу
enum PackageClass(val label: String):
  case Light extends PackageClass("Light")      // до 5 кг
  case Medium extends PackageClass("Medium")    // до 15 кг
  case Heavy extends PackageClass("Heavy")      // до 30 кг
```

**Таблица классификации посылок:**

| Класс | Диапазон | Упаковка | Описание |
|-------|----------|----------|----------|
| Light | 0 - 5 кг | Envelope | Письма, документы |
| Medium | 5 - 15 кг | Box | Посылки среднего размера |
| Heavy | 15 - 30 кг | Crate | Тяжёлые грузы |

// Конфигурация почтовой системы
case class PostConfig(
  taxPerKg:          Double,    // цена за кг (50 руб)
  maxWeightKg:       Double,    // макс вес (30 кг)
  storageRatePerDay: Double,    // хранение в день (10 руб)
  weightClasses:     List[(Double, PackageClass)],  // таблица классов
  packagingRules:    Map[PackageClass, PackagingRule] // таблица упаковок
)

// ID посылки
case class ParcelId(value: Int)

// Сама посылка
case class Parcel(
  id:         ParcelId,
  sender:     String,
  recipient:  String,
  weightKg:   Double,
  pkgClass:   PackageClass,
  packaging:  PackagingRule,
  acceptedDay: Int
)

// Расширение для Parcel
extension (p: Parcel)
  def storedDays(currentDay: Int): Int = currentDay - p.acceptedDay
  def summary: String = s"#${p.id.value}: ${p.sender} -> ${p.recipient}, ${p.weightKg} kg"

// Состояние почты
case class PostState(
  parcels:   Map[ParcelId, Parcel],  // посылки в хранилище
  issued:    List[Parcel],           // выданные посылки
  revenue:   Double,                 // заработок
  currentDay: Int,                   // текущий день
  nextId:    Int                     // следующий ID
)
```

**Ключевые концепции**:
- `case class` = автоматические equals, hashCode, copy
- `enum` = типобезопасные альтернативы
- `extension` = методы к существующим типам

---

### 2️⃣ MONAD СЛОЙ (Monad.scala)
**Назначение**: определяет типовой класс `Monad` для работы с эффектами.

```scala
trait Monad[F[_]]:
  // "Заверни обычное значение в контейнер"
  def pure[A](a: A): F[A]
  
  // "Чейнинг: возьми результат из fa, передай в f"
  // Карирование (два набора параметров) нужно для:
  // 1. Type inference (компилятор выводит типы лучше)
  // 2. For comprehension синтаксиса
  // 3. Implicit параметров (using)
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  
  // "Трансформируй значение внутри контейнера"
  def map[A, B](fa: F[A])(f: A => B): F[B] = 
    flatMap(fa)(a => pure(f(a)))
  
  // "Выполни первую операцию, забудь результат, выполни вторую"
  def andThen[A, B](fa: F[A])(fb: F[B]): F[B] = 
    flatMap(fa)(_ => fb)

object Monad:
  def apply[F[_]](using ev: Monad[F]): Monad[F] = ev
  
  // Extension методы для удобства: fa.flatMap вместо m.flatMap(fa)
  extension [F[_], A](fa: F[A])(using m: Monad[F])
    def map[B](f: A => B): F[B] = m.map(fa)(f)
    def flatMap[B](f: A => F[B]): F[B] = m.flatMap(fa)(f)
    def >>[B](fb: F[B]): F[B] = m.andThen(fa)(fb)  // оператор для чейнинга
```

**For comprehension синтаксис**:
```scala
for {
  a <- fa          // fa.flatMap(a =>
  b <- fb          //   fb.flatMap(b =>
} yield c          //     pure(c)))

// Эквивалентно:
fa.flatMap(a => fb.flatMap(b => Monad[F].pure(c)))
```

**Таблица: как for comprehension раскрывается:**

| Синтаксис | Раскрытие | Что происходит |
|-----------|-----------|----------------|
| `a <- fa` | `fa.flatMap(a =>` | Запусти fa, получи результат a |
| `b <- fb` | `fb.flatMap(b =>` | Запусти fb, получи результат b |
| `yield c` | `pure(c)))` | Верни c в контейнере |

**Пример в PostState:**
```scala
for {
  weight <- console.readLine      // AppF[String]
  ok     <- cfg.canAccept(...)    // AppF[Boolean]
  cost   <- cfg.acceptanceCost    // AppF[Double]
} yield (weight, ok, cost)        // AppF[(String, Boolean, Double)]

// Раскрытие:
console.readLine.flatMap(weight =>
  cfg.canAccept(...).flatMap(ok =>
    cfg.acceptanceCost(...).flatMap(cost =>
      Monad[AppF].pure((weight, ok, cost))
    )
  )
)
```

**Ключевая идея**: `F[_]` это контейнер для значений. `Monad` описывает как работать с ЛЮБЫМ контейнером.

---

### 3️⃣ AppF СЛОЙ (AppF.scala)
**Назначение**: реализация State monad БЕЗ внешних библиотек.

```scala
// AppF это просто функция: возьми состояние, верни новое состояние + результат
type AppF[A] = PostState => (PostState, A)

// Изменить состояние, не возвращать ничего полезного
def stateModify(f: PostState => PostState): AppF[Unit] =
  s => (f(s), ())

// Прочитать значение из состояния, не менять состояние
def stateInspect[A](f: PostState => A): AppF[A] =
  s => (s, f(s))

// Вернуть значение, не менять состояние
def statePure[A](a: A): AppF[A] =
  s => (s, a)

// Реализация Monad для AppF
given Monad[AppF] with
  def pure[A](a: A): AppF[A] = statePure(a)
  
  def flatMap[A, B](fa: AppF[A])(f: A => AppF[B]): AppF[B] =
    s =>
      val (s1, a) = fa(s)    // запустили fa, получили новое состояние и результат
      f(a)(s1)               // передали результат в f, запустили с новым состоянием
```

**Как это работает**:
```
Начальное состояние s0
       ↓
fa(s0) → (s1, a)      // fa изменила состояние на s1, вернула a
       ↓
f(a)(s1) → (s2, b)    // f(a) изменила состояние на s2, вернула b
       ↓
Финальное состояние s2
```

**Ключевое понимание**: состояние передаётся от операции к операции как цепь вызовов.

**Диаграмма потока состояния:**

```
PostState s0 (начальное состояние)
    ↓
fa(s0) запущена
    ↓
Вычисление: (s1, результат_A)
    ↓
f(результат_A)(s1) запущена
    ↓
Вычисление: (s2, результат_B)
    ↓
PostState s2 (финальное состояние с обновлениями)

Пример:
s0 = {parcels: [], revenue: 0, currentDay: 1, nextId: 1}
  ↓
acceptParcel → (s1 = {parcels: [parcel_1], revenue: 500}, ())
  ↓
logAcceptance → (s1 = {parcels: [parcel_1], revenue: 500}, ())
  ↓
putStrLn → (s1, ())
  ↓
s1 = {parcels: [parcel_1], revenue: 500, currentDay: 1, nextId: 2}
```

---

### 4️⃣ ALGEBRAS СЛОЙ
**Назначение**: определяет ЧТО система делает (интерфейсы).

#### ConfigAlgebra.scala
```scala
trait ConfigAlgebra[F[_]]:
  def acceptanceCost(weightKg: Double): F[Double]
  def canAccept(weightKg: Double): F[Boolean]
  def storageCost(days: Int): F[Double]
  def packageClass(weightKg: Double): F[(PackageClass, PackagingRule)]
```
→ Расчёты по формулам, никакого состояния.

#### StateAlgebra.scala
```scala
trait StateAlgebra[F[_]]:
  def acceptParcel(parcel: Parcel, cost: Double): F[Unit]
  def pickupParcel(parcelId: ParcelId, storageCost: Double): F[Unit]
  def allParcels: F[List[Parcel]]
  def allIssued: F[List[Parcel]]
  def revenue: F[Double]
  def currentDay: F[Int]
  def nextDay: F[Int]
```
→ Управление состоянием: добавить/удалить посылку, читать данные.

#### ConsoleAlgebra.scala
```scala
trait ConsoleAlgebra[F[_]]:
  def readLine: F[String]
  def putStr(s: String): F[Unit]
  def putStrLn(s: String): F[Unit]
```
→ Консоль: читать с клавиатуры, писать в консоль.

#### LogAlgebra.scala
```scala
trait LogAlgebra[F[_]]:
  def logAcceptance(parcel: Parcel, cost: Double): F[Unit]
  def logTariffCalc(weightKg: Double, cost: Double): F[Unit]
  def logRejection(recipient: String, weightKg: Double, reason: String): F[Unit]
  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): F[Unit]
  def logIssuance(parcel: Parcel, dayIssued: Int): F[Unit]
```
→ Логирование: записать события.

#### IdSourceAlgebra.scala
```scala
trait IdSourceAlgebra[F[_]]:
  def nextId: F[ParcelId]
```
→ Генератор ID: получить следующий ID.

**Ключевая идея**: все trait'ы параметризованы на `F[_]`. Если `F = AppF`, то операции работают с состоянием. Если `F = Option`, то могут завалиться. Если `F = Id`, то синхронные.

**Таблица Algebras:**

| Algebra | Назначение | Методы |
|---------|-----------|--------|
| **ConfigAlgebra** | Расчёты по формулам | `acceptanceCost`, `canAccept`, `storageCost`, `packageClass` |
| **StateAlgebra** | Управление состоянием | `acceptParcel`, `pickupParcel`, `allParcels`, `allIssued`, `revenue`, `currentDay`, `nextDay` |
| **ConsoleAlgebra** | Консоль I/O | `readLine`, `putStr`, `putStrLn` |
| **LogAlgebra** | Логирование событий | `logAcceptance`, `logTariffCalc`, `logRejection`, `logStorageCharge`, `logIssuance` |
| **IdSourceAlgebra** | Генерация ID | `nextId` |

---

### 5️⃣ INTERPRETERS СЛОЙ
**Назначение**: реализации операций (как система это делает).

#### ConfigInterpreter.scala
```scala
final class ConfigInterpreter(cfg: PostConfig) extends ConfigAlgebra[AppF]:
  def acceptanceCost(weightKg: Double): AppF[Double] =
    statePure(weightKg * cfg.taxPerKg)
  
  def canAccept(weightKg: Double): AppF[Boolean] =
    statePure(weightKg > 0 && weightKg <= cfg.maxWeightKg)
  
  def storageCost(days: Int): AppF[Double] =
    statePure(days * cfg.storageRatePerDay)
  
  def packageClass(weightKg: Double): AppF[(PackageClass, PackagingRule)] =
    statePure {
      val cls = cfg.weightClasses.find((t, _) => weightKg <= t).map(_(1)).getOrElse(PackageClass.Heavy)
      val rule = cfg.packagingRules.getOrElse(cls, PackagingRule.Crate)
      (cls, rule)
    }
```
**Замечание**: `class`, потому что нужен параметр `cfg`.

#### MockConfigInterpreter.scala
```scala
object MockConfigInterpreter extends ConfigAlgebra[AppF]:
  def acceptanceCost(weightKg: Double): AppF[Double] = 
    statePure(10.0)  // Всё дешевле!
  def canAccept(weightKg: Double): AppF[Boolean] = 
    statePure(weightKg > 0 && weightKg <= 50.0)
  def storageCost(days: Int): AppF[Double] = 
    statePure(1.0)
  def packageClass(weightKg: Double): AppF[(PackageClass, PackagingRule)] = 
    statePure((PackageClass.Medium, PackagingRule.Box))
```
**Замечание**: `object`, потому что чистая логика без параметров.

#### StateInterpreter.scala
```scala
object StateInterpreter extends StateAlgebra[AppF]:
  def acceptParcel(parcel: Parcel, cost: Double): AppF[Unit] =
    stateModify(s => s.copy(
      parcels = s.parcels + (parcel.id -> parcel),
      revenue = s.revenue + cost
    ))
  
  def pickupParcel(parcelId: ParcelId, storageCost: Double): AppF[Unit] =
    stateModify { s =>
      s.parcels.get(parcelId).fold(s) { p =>
        s.copy(
          parcels = s.parcels - p.id,
          issued = s.issued :+ p,
          revenue = s.revenue + storageCost
        )
      }
    }
  
  def allParcels: AppF[List[Parcel]] = stateInspect(_.parcels.values.toList)
  def allIssued: AppF[List[Parcel]] = stateInspect(_.issued)
  def revenue: AppF[Double] = stateInspect(_.revenue)
  def currentDay: AppF[Int] = stateInspect(_.currentDay)
  
  def nextDay: AppF[Int] =
    s => {
      val d = s.currentDay + 1
      (s.copy(currentDay = d), d)
    }
```

#### ConsoleInterpreter.scala
```scala
object ConsoleInterpreter extends ConsoleAlgebra[AppF]:
  def readLine: AppF[String] =
    s => (s, Option(scala.io.StdIn.readLine()).getOrElse(""))
  
  def putStr(msg: String): AppF[Unit] =
    s => { print(msg); (s, ()) }
  
  def putStrLn(msg: String): AppF[Unit] = 
    putStr(msg + "\n")
```

#### LogInterpreter.scala
```scala
object LogInterpreter extends LogAlgebra[AppF]:
  private def log(msg: String): AppF[Unit] = s => { println(msg); (s, ()) }
  
  def logAcceptance(parcel: Parcel, cost: Double): AppF[Unit] =
    log(s"[LOG] Accepted #${parcel.id.value}: ${parcel.sender} -> ${parcel.recipient}, ${parcel.weightKg} kg")
  
  def logTariffCalc(weightKg: Double, cost: Double): AppF[Unit] =
    log(s"[LOG] Tariff: $weightKg kg = $cost rub.")
  
  def logRejection(recipient: String, weightKg: Double, reason: String): AppF[Unit] =
    log(s"[LOG] REJECTED recipient=$recipient weight=${weightKg}kg reason=$reason")
  
  def logStorageCharge(parcelId: ParcelId, days: Int, cost: Double): AppF[Unit] =
    log(s"[LOG] Storage #${parcelId.value}: $days days = $cost rub.")
  
  def logIssuance(parcel: Parcel, dayIssued: Int): AppF[Unit] =
    log(s"[LOG] Issued #${parcel.id.value} to ${parcel.recipient} (day $dayIssued)")
```

#### IdSourceInterpreter.scala
```scala
object IdSourceInterpreter extends IdSourceAlgebra[AppF]:
  def nextId: AppF[ParcelId] =
    s => (s.copy(nextId = s.nextId + 1), ParcelId(s.nextId))
```

#### Interpreters.scala
```scala
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
      cfg     = ConfigInterpreter(cfg),        // Можно заменить на MockConfigInterpreter!
      log     = LogInterpreter,
      state   = StateInterpreter,
      console = ConsoleInterpreter,
      id      = IdSourceInterpreter,
    )
```

---

## 📱 PROGRAM СЛОЙ (Program.scala)

**Назначение**: бизнес-логика. Использует algebras через `using`, не знает о конкретных интерпретаторах.

```scala
// Поток приёма посылки
def acceptFlow[F[_]](using
  cfg:     ConfigAlgebra[F],
  state:   StateAlgebra[F],
  log:     LogAlgebra[F],
  console: ConsoleAlgebra[F],
  id:      IdSourceAlgebra[F],
)(using Monad[F]): F[Unit] =
  for
    sender    <- console.putStr("Sender name: ")        >> console.readLine
    recipient <- console.putStr("Recipient name: ")     >> console.readLine
    wStr      <- console.putStr("Parcel weight (kg): ") >> console.readLine
    weight     = wStr.toDoubleOption.getOrElse(0.0)
    ok        <- cfg.canAccept(weight)
    _         <- if ok then acceptValid(sender, recipient, weight)
                 else
                   log.logRejection(recipient, weight, "weight exceeds maximum") >>
                   console.putStrLn(s"Rejected: weight $weight kg exceeds maximum.")
  yield ()

// Поток выдачи посылки
def pickupFlow[F[_]](using
  cfg:     ConfigAlgebra[F],
  state:   StateAlgebra[F],
  log:     LogAlgebra[F],
  console: ConsoleAlgebra[F],
)(using Monad[F]): F[Unit] =
  for
    parcels <- state.allParcels
    _       <- if parcels.isEmpty then console.putStrLn("No parcels to issue.")
               else issueFromList(parcels)
  yield ()

// Переход на следующий день
def nextDayFlow[F[_]](using
  state:   StateAlgebra[F],
  console: ConsoleAlgebra[F],
)(using Monad[F]): F[Unit] =
  for
    day <- state.nextDay
    _   <- console.putStrLn(s"Day $day started.")
  yield ()

// Сводка результатов
def summaryFlow[F[_]](using
  state:   StateAlgebra[F],
  console: ConsoleAlgebra[F],
)(using Monad[F]): F[Unit] =
  for
    issued  <- state.allIssued
    revenue <- state.revenue
    _       <- console.putStrLn(
                 if issued.nonEmpty then
                   s"Issued parcels: ${issued.map(_.summary).mkString(", ")}\nTotal revenue: ${"%.2f".format(revenue)} rub. Goodbye!"
                 else
                   s"Total revenue: ${"%.2f".format(revenue)} rub. Goodbye!"
               )
  yield ()

// Динамический заголовок меню
def statusTitle[F[_]](using state: StateAlgebra[F])(using Monad[F]): F[String] =
  for
    day     <- state.currentDay
    revenue <- state.revenue
    parcels <- state.allParcels
  yield s"Post Office Day $day | Revenue: ${"%.2f".format(revenue)} rub. | Stored: ${parcels.size}"

// Сборка меню
def buildMenu[F[_]](using algebras...)(using Monad[F]): MenuTreeNode[F] =
  MenuTreeNode[F](
    titleF = statusTitle[F],
    children = Seq(
      MenuLeaf("Accept parcel", acceptFlow),
      MenuLeaf("Issue parcel",  pickupFlow),
      MenuLeaf("Next day",      nextDayFlow),
      MenuLeaf("Show summary",  summaryFlow),
    ),
  )

// Главный цикл меню
def menu[F[_]](using algebras...)(using Monad[F]): F[Unit] =
  buildMenu[F].userInteractionLoop
```

**Ключевые моменты**:
- Функции параметризованы на `F[_]` — работают с ЛЮБЫМ контейнером
- `using` — dependency injection algebras через контекст
- `for comprehension` — readable синтаксис для чейнинга операций
- Функция НЕ знает что такое AppF, StateInterpreter и т.д.

**Диаграмма вызовов Program.acceptFlow:**

```
Program.acceptFlow[AppF]
    ↓
console.putStr("Sender: ") >> console.readLine
    ↓ (ConsoleInterpreter)
    ↓ Пользователь вводит: "Alice"
    ↓
console.putStr("Recipient: ") >> console.readLine
    ↓ (ConsoleInterpreter)
    ↓ Пользователь вводит: "Bob"
    ↓
console.putStr("Weight: ") >> console.readLine
    ↓ (ConsoleInterpreter)
    ↓ Пользователь вводит: "10"
    ↓
cfg.canAccept(10.0)
    ↓ (ConfigInterpreter)
    ↓ Результат: true (10 ≤ 30)
    ↓
if ok then acceptValid(...)
    ↓
cfg.acceptanceCost(10.0)
    ↓ (ConfigInterpreter)
    ↓ Результат: 500.0 (10 * 50)
    ↓
cfg.packageClass(10.0)
    ↓ (ConfigInterpreter)
    ↓ Результат: (Medium, Box)
    ↓
id.nextId
    ↓ (IdSourceInterpreter)
    ↓ Результат: ParcelId(1), состояние: nextId = 2
    ↓
state.currentDay
    ↓ (StateInterpreter)
    ↓ Результат: 1
    ↓
state.acceptParcel(parcel, 500.0)
    ↓ (StateInterpreter)
    ↓ Изменение состояния: parcels + revenue
    ↓
log.logAcceptance(parcel, 500.0)
    ↓ (LogInterpreter)
    ↓ Печать: "[LOG] Accepted #1: Alice -> Bob, 10.0 kg"
    ↓
console.putStrLn("Receipt...")
    ↓ (ConsoleInterpreter)
    ↓ Печать квитанции
```

---

## 🎮 MENU СЛОЙ (Menu.scala)

**Назначение**: UI навигация (древовидное меню).

```scala
sealed trait MenuOption:
  def show: String

trait UserInteraction[F[_]]:
  def handleUserAnswer(answer: String): F[Unit]
  def userInteractionLoop: F[Unit]

// Листья дерева меню (конкретные операции)
final case class MenuLeaf[F[_]](title: String, action: F[Unit]) extends MenuOption:
  def show: String = title

// Узлы дерева меню (подменю с дочерними опциями)
final case class MenuTreeNode[F[_]: Monad](
  titleF:   F[String],
  children: Seq[MenuOption],
)(using console: ConsoleAlgebra[F])
  extends MenuOption with UserInteraction[F]:

  def show: String = "menu"

  private def printMenu: F[Unit] =
    for
      title <- titleF
      lines  = children.zipWithIndex.map { (opt, i) => s"${i + 1}) ${opt.show}" }
      body   = (lines :+ "0) Exit").mkString("\n")
      _     <- console.putStrLn(s"\n=== $title ===\n$body")
    yield ()

  def handleUserAnswer(answer: String): F[Unit] =
    answer.trim match
      case "0" | "" =>
        Monad[F].pure(())
      case s =>
        s.toIntOption match
          case Some(n) if n >= 1 && n <= children.size =>
            children(n - 1) match
              case leaf: MenuLeaf[F @unchecked]     => leaf.action >> userInteractionLoop
              case node: MenuTreeNode[F @unchecked] => node.userInteractionLoop >> userInteractionLoop
          case _ =>
            console.putStrLn(s"Unknown command: '$s'") >> userInteractionLoop

  def userInteractionLoop: F[Unit] =
    for
      _      <- printMenu
      answer <- console.readLine
      _      <- handleUserAnswer(answer)
    yield ()
```

---

## 🚀 MAIN СЛОЙ (Main.scala)

**Назначение**: точка входа, "склейка" всех частей.

```scala
@main def main(): Unit =
  val alg = Interpreters.make(PostConfig.default)
  Program.menu[AppF](using alg.cfg, alg.state, alg.log, alg.console, alg.id)
    .apply(PostState.empty)
```

**Что здесь происходит**:
1. `Interpreters.make(PostConfig.default)` — создаёт реальные интерпретаторы
2. `Program.menu[AppF](using alg.cfg, ...)` — передаёт интерпретаторы в программу
3. `.apply(PostState.empty)` — запускает функцию AppF[Unit] с пустым состоянием

---

## 💡 ПОЧЕМУ ЭТО МОЩНО: Tagless Final Pattern

### Проблема
```scala
// Неправильно: код зависит от ConcreteState
def acceptFlow: ConcreteState => (ConcreteState, Unit) = {
  // Тяжело тестировать, тяжело менять реализацию
}
```

### Решение
```scala
// Правильно: код не зависит от AppF или любого другого F
def acceptFlow[F[_]](using cfg: ConfigAlgebra[F], ...)(using Monad[F]): F[Unit] = {
  // Работает с ЛЮБЫМ F, который имеет ConfigAlgebra и Monad!
}

// Одна программа, ТРИ разные реализации:
val alg1 = Algebras(cfg = ConfigInterpreter(PostConfig.default), ...)
val alg2 = Algebras(cfg = MockConfigInterpreter, ...)  // Цены в 5 раз дешевле
val alg3 = Algebras(cfg = ..., console = TestConsoleInterpreter, ...)  // Для тестов

// acceptFlow работает со ВСЕМИ!
```

### Преимущества
✅ **Тестирование**: используй Mock интерпретаторы вместо реальных  
✅ **Разработка**: отдельно логика, отдельно I/O  
✅ **Разные режимы**: одна программа, разные интерпретаторы  
✅ **Переиспользование**: Program.acceptFlow работает везде  
✅ **Типобезопасность**: компилятор проверяет всё  

---

## 🔑 КЛЮЧЕВЫЕ КОНЦЕПЦИИ

### Карирование (Currying)
```scala
// Вместо: flatMap(fa, f)
def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

// Почему?
// 1. Type inference: компилятор выводит [A, B] из (fa), потом f
// 2. For comprehension: fa <- ... для flatMap
// 3. Implicit параметры: using Monad[F] передаётся вторым набором
```

### Type Constructor F[_]
```scala
// F[_] это контейнер для значений
List[_]      // контейнер: может содержать много значений
Option[_]    // контейнер: может содержать 0 или 1 значение
AppF[_]      // контейнер: состояние + результат
Id[_]        // контейнер: просто значение (identity)

// Monad[F] говорит: "я знаю как работать с F"
```

**Таблица контейнеров и их поведения:**

| Контейнер | Тип | Значение | Monad[F] означает |
|-----------|-----|----------|-------------------|
| `List[Int]` | Много значений | [1, 2, 3] | Применить операцию к каждому элементу |
| `Option[Int]` | 0 или 1 | Some(5) или None | Если Some, применить; если None, пропустить |
| `AppF[Int]` | Состояние + результат | (PostState, 42) | Передать состояние между операциями |
| `Either[E, A]` | Результат или ошибка | Left(error) или Right(5) | Если Right, продолжить; если Left, остановить |

**Пример с AppF:**
```scala
val result: AppF[Int] = s => (s.copy(nextId = s.nextId + 1), s.nextId)
// F[_] = AppF
// Контейнер содержит: (PostState, Int)
```

### Extension методы
```scala
extension [F[_], A](fa: F[A])(using m: Monad[F])
  def flatMap[B](f: A => F[B]): F[B] = m.flatMap(fa)(f)

// Позволяет писать:
fa.flatMap(f)  // вместо m.flatMap(fa)(f)

// Это синтаксический сахар
```

### Using / Given (Dependency Injection)
```scala
def acceptFlow[F[_]](using cfg: ConfigAlgebra[F]): F[Unit]
// "Я нуждаюсь в ConfigAlgebra[F], 
//  найди её в контексте и передай мне"

// Когда вызываешь:
acceptFlow[AppF]
// Компилятор ищет: ConfigAlgebra[AppF] в scope
// Находит: object ConfigInterpreter
```

**Таблица: Как Using работает в нашем коде:**

| Запрос | Using параметр | Передача | Как компилятор находит |
|--------|---|---|---|
| `acceptFlow[AppF]` | `using cfg: ConfigAlgebra[AppF]` | `cfg = ConfigInterpreter(cfg)` | Поиск в Interpreters.make() |
| `Program.menu[AppF]` | `using alg.cfg, alg.state, ...` | Явно передаёшь из Main | `val alg = Interpreters.make(...)` |
| `fa.flatMap(f)` | `using m: Monad[AppF]` | Автоматически | `given Monad[AppF]` в AppF.scala |

**Таблица: Extension методы vs обычные методы:**

| Вызов | Расширение (Extension) | Обычный метод |
|------|---|---|
| `fa.flatMap(f)` | `m.flatMap(fa)(f)` | `monad.flatMap(fa, f)` |
| `fa.map(f)` | `m.map(fa)(f)` | `monad.map(fa, f)` |
| `fa >> fb` | `m.andThen(fa)(fb)` | `monad.andThen(fa, fb)` |
| Читаемость | Высокая (как в Ruby/Python) | Низкая (как в Java) |
| Type inference | Лучше | Хуже |

**Пример: как extension методы работают:**

```scala
// Extension метод в Monad.scala:
extension [F[_], A](fa: F[A])(using m: Monad[F])
  def flatMap[B](f: A => F[B]): F[B] = m.flatMap(fa)(f)

// Использование 1: прямой вызов
val result1 = m.flatMap(fa)(f)

// Использование 2: как метод (extension)
val result2 = fa.flatMap(f)

// Оба эквивалентны! Extension просто сахар.
```

---

## 🐛 ИСПРАВЛЕННЫЕ ОШИБКИ

| Файл | Ошибка | Исправление | Причина |
|------|--------|-------------|---------|
| ConfigAlgebra.scala | `storageCost(weight: Double)` | `storageCost(days: Int)` | Хранение считается по дням, не по весу |
| LogAlgebra.scala | `logStorageChange` | `logStorageCharge` | Правильное название операции |
| ConsoleInterpreter.scala | `object ConsoleIntepreter` | `object ConsoleInterpreter` | Опечатка: "interpreter" а не "intepreter" |
| ConfigInterpreter.scala | `def acceptanceCost(weight)` | `def acceptanceCost(weightKg: Double)` | Нужны имена и типы параметров |
| build.sbt | `libraryDependencies ++= Seq()` | (пусто) | Нет внешних зависимостей |
| Program.scala | `cfg.packageClass.weightKg` | `cfg.packageClass(weightKg)` | Неправильный синтаксис вызова |
| StateInterpreter.scala | `s.parcel.get(parcelId)` | `s.parcels.get(parcelId)` | Множественное число: parcels |
| AppF.scala | Отсутствовал `given Monad[AppF]` | Добавлена реализация | Без этого for comprehension не работает |

**Пример ошибки 1:**
```scala
// НЕПРАВИЛЬНО:
def storageCost(weight: Double): AppF[Double] = 
  statePure(weight * cfg.storageRatePerDay)  // ❌ weight?

// ПРАВИЛЬНО:
def storageCost(days: Int): AppF[Double] = 
  statePure(days * cfg.storageRatePerDay)    // ✅ days!

// Использование:
storageCost(2)  // 2 дня × 10 руб/день = 20 руб
```

**Пример ошибки 3:**
```scala
// НЕПРАВИЛЬНО:
object ConsoleIntepreter extends ConsoleAlgebra[AppF]:  // ❌ Intepreter

// ПРАВИЛЬНО:
object ConsoleInterpreter extends ConsoleAlgebra[AppF]:  // ✅ Interpreter
```

---

## 📚 КАК ОБЪЯСНИТЬ ПРЕПОДУ

> "Профессор, вот архитектура:
> 
> 1. **Domain** — данные (посылки, состояние, конфиг)
> 2. **Monad** — типовой класс для чейнинга операций
> 3. **AppF** — наша реализация State monad (без Cats!)
> 4. **Algebras** — интерфейсы: ЧТО система делает
> 5. **Interpreters** — реализации: КАК система это делает
> 6. **Program** — бизнес-логика: использует algebras, не знает о конкретных интерпретаторах
> 7. **Menu** — UI: древовидное меню с навигацией
> 8. **Main** — точка входа: "склейка" всех частей
> 
> **Главная фишка**: Tagless Final pattern.
> - **Реальная цена 50 рублей/кг**? Используй ConfigInterpreter.
> - **Хочешь тест с ценой 10 рублей/кг**? Используй MockConfigInterpreter.
> - **Program.acceptFlow не меняется**! Просто передаёшь разные интерпретаторы.
> 
> Это полиморфизм на уровне типов: одна программа, разные реализации."

---

## 🧪 ТЕСТИРОВАНИЕ

**Диаграмма жизненного цикла посылки:**

```
День 1: Приём (Accept)
  ┌─────────────────────────────────┐
  │ Alice отправляет посылку Bob    │
  │ Вес: 10 кг                      │
  │ Класс: Medium (5-15 кг)         │
  │ Упаковка: Box                   │
  │ Цена: 10 * 50 = 500 рублей     │
  │ ID: #1                          │
  │ День приёма: 1                  │
  └─────────────────────────────────┘
           ↓ (состояние)
    parcels: {#1 → Parcel}
    revenue: 500 руб
           ↓
День 2: Хранение
  ┌─────────────────────────────────┐
  │ Посылка #1 лежит в хранилище    │
  │ Хранится 1 день (Day 2 - Day 1) │
  └─────────────────────────────────┘
           ↓
День 3: Выдача (Issue)
  ┌─────────────────────────────────┐
  │ Bob забирает посылку            │
  │ Дней хранения: 2                │
  │ Плата за хранение: 2 * 10 = 20р │
  │ Общая выручка: 500 + 20 = 520р  │
  │ Посылка удаляется из хранилища  │
  └─────────────────────────────────┘
           ↓ (состояние)
    parcels: {}
    issued: [#1]
    revenue: 520 руб
```

### Пример 1: Приёмка посылки (реальная цена)
```
День 1, Доход: 0 руб, Посылок: 0
1) Accept parcel
2) Issue parcel
...
Выбираешь: 1 (Accept)
  Sender: Alice
  Recipient: Bob
  Weight: 10 kg
  
Система считает:
  - Класс: Medium (10 ≤ 15)
  - Упаковка: Box
  - Цена: 10 * 50 = 500 руб
  - ID посылки: 1
  - Добавляет в состояние

День 1, Доход: 500 руб, Посылок: 1
```

**Трансформация состояния при приёме:**

```
БЫЛО (PostState.empty):
┌─────────────────────────────────┐
│ parcels:   {}                   │
│ issued:    []                   │
│ revenue:   0.0                  │
│ currentDay: 1                   │
│ nextId:    1                    │
└─────────────────────────────────┘
                ↓
          acceptFlow
        (Alice, Bob, 10.0)
                ↓
          StateInterpreter
          .acceptParcel()
                ↓
          СТАЛО (PostState):
┌─────────────────────────────────┐
│ parcels:   {                    │
│   1 → Parcel(                   │
│     id=1,                       │
│     sender="Alice",             │
│     recipient="Bob",            │
│     weight=10.0,                │
│     class=Medium,               │
│     packaging=Box,              │
│     acceptedDay=1               │
│   )                             │
│ }                               │
│ issued:    []                   │
│ revenue:   500.0                │
│ currentDay: 1                   │
│ nextId:    2  ← изменилось!     │
└─────────────────────────────────┘
```

### Пример 2: С Mock интерпретатором
```
Меняешь в Main:
val alg = Interpreters.make(PostConfig.default)
На:
val alg = Algebras(
  cfg = MockConfigInterpreter,  // Цена: 10 руб вместо 500!
  state = StateInterpreter,
  ...
)

День 1, Доход: 10 руб, Посылок: 1  // Дешевле в 50 раз!
```

Program.acceptFlow **не изменилась**!

**Таблица сравнения: Реальный vs Mock интерпретатор**

| Параметр | ConfigInterpreter | MockConfigInterpreter |
|----------|-------------------|----------------------|
| **Цена/кг** | 50 руб | 10 руб |
| **Макс вес** | 30 кг | 50 кг |
| **Хранение/день** | 10 руб | 1 руб |
| **Класс упаковки** | Зависит от веса | Всегда Medium + Box |
| **Использование** | Реальная система | Тестирование |
| **Пример: 10 кг** | 500 рублей | 100 рублей |
| **Пример: 20 кг** | Rejected (>30) | 200 рублей, Medium |

**Пример сценария с реальной ценой:**
```
Приёмка: 10 кг → 500 руб (день 1)
Хранение: 2 дня → 20 руб
Выдача: итого 520 руб (день 3)
```

**Тот же сценарий с Mock ценой:**
```
Приёмка: 10 кг → 100 руб (день 1)
Хранение: 2 дня → 2 руб
Выдача: итого 102 руб (день 3)
```

---

## 📁 ФАЙЛОВАЯ СТРУКТУРА

```
src/main/scala/postoffice/
├── Domain.scala                 # Типы данных
├── Monad.scala                  # Типовой класс Monad
├── Main.scala                   # Точка входа
├── Program.scala                # Бизнес-логика
├── Menu.scala                   # UI навигация
│
├── algebras/
│   ├── ConfigAlgebra.scala      # Интерфейс конфигурации
│   ├── StateAlgebra.scala       # Интерфейс состояния
│   ├── ConsoleAlgebra.scala     # Интерфейс консоли
│   ├── LogAlgebra.scala         # Интерфейс логирования
│   └── IdSourceAlgebra.scala    # Интерфейс генератора ID
│
└── interpreters/
    ├── AppF.scala               # Реализация State monad
    ├── ConfigInterpreter.scala  # Реализация ConfigAlgebra (реальная)
    ├── mock.scala               # MockConfigInterpreter (для тестов)
    ├── StateInterpreter.scala   # Реализация StateAlgebra
    ├── ConsoleInterpreter.scala # Реализация ConsoleAlgebra
    ├── LogInterpreter.scala     # Реализация LogAlgebra
    ├── IdSourceInterpreter.scala# Реализация IdSourceAlgebra
    └── Interpreters.scala       # Сборка всех интерпретаторов
```

**Таблица ролей файлов:**

| Файл | Тип | Ответственность | Зависит от |
|------|-----|-----------------|-----------|
| Domain.scala | Data | Определяет все типы | Ничего |
| Monad.scala | Typeclass | Правила работы с контейнерами | Ничего |
| AppF.scala | Implementation | State monad + Monad instance | Domain, Monad |
| ConfigAlgebra.scala | Interface | Что система считает | Domain |
| StateAlgebra.scala | Interface | Что система хранит | Domain |
| ConsoleAlgebra.scala | Interface | Что система читает/пишет | Ничего |
| LogAlgebra.scala | Interface | Что система логирует | Domain |
| IdSourceAlgebra.scala | Interface | Что система генерирует | Domain |
| ConfigInterpreter.scala | Implementation | Как считать (реальная логика) | AppF, ConfigAlgebra, Domain |
| MockConfigInterpreter | Implementation | Как считать (упрощённая) | AppF, ConfigAlgebra, Domain |
| StateInterpreter.scala | Implementation | Как хранить | AppF, StateAlgebra |
| ConsoleInterpreter.scala | Implementation | Как читать/писать | AppF, ConsoleAlgebra |
| LogInterpreter.scala | Implementation | Как логировать | AppF, LogAlgebra |
| IdSourceInterpreter.scala | Implementation | Как генерировать ID | AppF, IdSourceAlgebra |
| Interpreters.scala | Factory | Собрать все интерпретаторы | Все выше |
| Program.scala | Business Logic | Бизнес-процессы | Algebras, Monad |
| Menu.scala | UI | Навигация по меню | ConsoleAlgebra, Monad |
| Main.scala | Bootstrap | Запустить приложение | Program, Interpreters, Domain |

---

## 🔄 ДИАГРАММА ЗАВИСИМОСТЕЙ

```
                     Main.scala
                         ↓
                  Interpreters.make()
                    /    |     |    \  \
                   /     |     |     \   \
         ConfigI. StateI. ConI. LogI. IdI.
             ↓      ↓     ↓     ↓    ↓
         ConfigA. StateA. ConA. LogA. IdA.
             \      |     |     |    /
              \     |     |     |   /
               \    |     |     |  /
                \   |     |     | /
                 Program.menu[AppF]
                      ↓
                   Menu.scala
                      ↓
                 userInteractionLoop
                      ↓
            Program.acceptFlow/pickupFlow/...
                      ↓
                   AppF[Unit]
                      ↓
                PostState.empty
                      ↓
                execute(state)

Легенда:
- Algebra = trait (интерфейс)
- Interpreter = object/class (реализация)
- AppF = функция (состояние в действии)
```

**Таблица: Как запрос проходит через систему:**

| Шаг | Что происходит | Компонент | Результат |
|-----|---|---|---|
| 1 | Пользователь выбирает "Accept parcel" | Menu.scala | MenuLeaf("Accept parcel", acceptFlow) |
| 2 | Вызывается acceptFlow | Program.scala | Запрос консоли |
| 3 | Консоль читает от пользователя | ConsoleInterpreter | String ("Alice") |
| 4 | acceptFlow получает String | Program.scala | Продолжает работу |
| 5 | acceptFlow вызывает canAccept | ConfigInterpreter | Boolean (true/false) |
| 6 | Если true, вызывает acceptValid | Program.scala | Дальнейшая работа |
| 7 | acceptValid запрашивает стоимость | ConfigInterpreter | Double (500.0) |
| 8 | acceptValid запрашивает ID | IdSourceInterpreter | ParcelId(1) |
| 9 | acceptValid сохраняет в состояние | StateInterpreter | PostState.copy(parcels=...) |
| 10 | acceptValid логирует событие | LogInterpreter | "[LOG] Accepted #1: ..." |
| 11 | acceptValid печатает квитанцию | ConsoleInterpreter | Receipt на экран |
| 12 | Возврат в Menu | Menu.scala | userInteractionLoop |

---

## ✨ ИТОГ

Это **чистая архитектура** на Scala 3:
- **Функциональная** (всё это функции и типы)
- **Типобезопасная** (компилятор всё проверяет)
- **Гибкая** (меняешь интерпретаторы, программа не меняется)
- **Без зависимостей** (Monad реализован вручную, AppF вручную)
- **Тестируемая** (используй Mock интерпретаторы)

Это и есть **Tagless Final** — мощный паттерн функционального программирования! 🚀

---

## 📝 ИТОГОВАЯ ТАБЛИЦА: ЧТО НУЖНО ЗАПОМНИТЬ

| Концепция | Определение | Пример | Для чего |
|-----------|---|---|---|
| **Domain** | Типы данных системы | PostState, Parcel, PostConfig | Описать данные |
| **Algebra** | Интерфейс операций | trait ConfigAlgebra | Описать ЧТО делать |
| **Interpreter** | Реализация операций | object ConfigInterpreter | Описать КАК делать |
| **Monad** | Типовой класс для чейнинга | flatMap, map, >> | Связывать операции |
| **AppF** | State monad в нашем коде | PostState => (PostState, A) | Проводить состояние |
| **For comprehension** | Синтаксис для монад | for { a <- fa; b <- fb } | Читаемый код вместо flatMap |
| **Using / Given** | Dependency injection | using cfg: ConfigAlgebra[F] | Передать реализацию |
| **Extension методы** | Методы к существующим типам | fa.flatMap(f) | Удобство синтаксиса |
| **Type constructor F[_]** | Параметризированный тип | List[_], Option[_], AppF[_] | Полиморфизм |
| **Tagless Final** | Паттерн архитектуры | Program работает с F[_], не AppF | Гибкость, тестируемость |

**Главный принцип:**
```
┌─────────────────────────────────────────┐
│ ОПИСАНИЕ (ЧТО?)                         │
│ ↓                                       │
│ Algebra traits:                         │
│ - ConfigAlgebra[F]                      │
│ - StateAlgebra[F]                       │
│ - ConsoleAlgebra[F]                     │
│                                         │
├─────────────────────────────────────────┤
│ РЕАЛИЗАЦИЯ (КАК?)                       │
│ ↓                                       │
│ Interpreters:                           │
│ - ConfigInterpreter                     │
│ - StateInterpreter                      │
│ - ConsoleInterpreter                    │
│ (или Mock версии для тестов!)           │
│                                         │
├─────────────────────────────────────────┤
│ РЕЗУЛЬТАТ                               │
│ ↓                                        │
│ Одна Program.acceptFlow работает        │
│ с ОБЕИМИ реализациями!                  │
│ Меняешь интерпретатор → меняется        │
│ поведение, программа неизменна!         │
└─────────────────────────────────────────┘
```

---

## 🎯 ЗАЩИТА ПЕРЕД ПРЕПОДАВАТЕЛЕМ: КРАТКИЙ СЦЕНАРИЙ

**Вопрос преподавателя:** "Почему ты так раздельил код?"

**Ответ:**
> Я использовал **Tagless Final pattern**. Это означает:
> 1. **Algebra** описывает ЧТО система делает (интерфейсы)
> 2. **Interpreter** описывает КАК система это делает (реализации)
> 3. **Program** использует algebras, не знает о конкретных интерпретаторах
>
> **Зачем?** Потому что одна программа может работать с разными реализациями:
> - ConfigInterpreter: цена 50 руб/кг (реальная)
> - MockConfigInterpreter: цена 10 руб/кг (для тестов)
>
> Program.acceptFlow не меняется! Просто передаёшь разные интерпретаторы.

**Вопрос:** "А что такое AppF?"

**Ответ:**
> Это **State monad** — функция `PostState => (PostState, A)`.
> - Берёт старое состояние
> - Возвращает новое состояние + результат
>
> Это позволяет передавать состояние между операциями как цепь:
> ```
> s0 → (s1, result1) → (s2, result2) → s2
> ```

**Вопрос:** "Почему Monad?"

**Ответ:**
> Потому что Monad определяет как чейнить операции (`flatMap`).
> Это даёт синтаксис `for comprehension`, который очень читаемый:
> ```scala
> for {
>   a <- operation1
>   b <- operation2(a)
> } yield result
> ```
> Вместо вложенных вызовов flatMap.

---

## 🔗 БЫСТРАЯ СПРАВКА: ФАЙЛ ДЛЯ БЕГЛОГО ПОИСКА

```
Нужна классификация посылок?        → Domain.scala, строка ~10
Нужна логика приёма?                → Program.scala, acceptFlow
Нужна логика выдачи?                → Program.scala, pickupFlow
Нужна логика меню?                  → Menu.scala, MenuTreeNode
Нужна конфигурация цен?             → ConfigInterpreter.scala
Нужна логика состояния?             → StateInterpreter.scala
Нужна логика консоли?               → ConsoleInterpreter.scala
Нужна логика логирования?           → LogInterpreter.scala
Нужна логика ID?                    → IdSourceInterpreter.scala
Нужна реализация монады?            → AppF.scala
Нужна сборка всего?                 → Interpreters.scala
```
