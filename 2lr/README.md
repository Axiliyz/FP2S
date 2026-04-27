# ЛР2: Tagless Final — Вариант 15. Почтовое отделение

## Запуск

```bash
sbt run
```

Требования: JDK 17+, sbt 1.9+.

---

## Структура проекта

```
src/main/scala/postoffice/
├── Domain.scala              # Доменные типы: PostConfig, Parcel, PostState, ...
├── Monad.scala               # Собственный минимальный Monad[F[_]] + синтаксис
├── algebras/
│   ├── ConfigAlgebra.scala   # Reader-алгебра: тарифы, правила упаковки
│   ├── LogAlgebra.scala      # Writer-алгебра: накопление лога
│   ├── StateAlgebra.scala    # State-алгебра: хранение посылок
│   └── ConsoleAlgebra.scala  # IO-алгебра: ввод-вывод с консолью
├── Program.scala             # Чистая бизнес-логика, полиморфна над F[_]
├── Interpreters.scala        # Конкретные IO-реализации алгебр (Ref, IO)
└── Main.scala                # Точка входа: сборка + конкретизация F = IO
```

---

## Три варианта Tagless Final и где они применены

### 1. С `F[_]` — полиморфизм над эффектом (`Program.scala`)

```scala
def acceptParcel[F[_]: Monad](recipient: String, weightKg: Double)(
  using cfg: ConfigAlgebra[F], state: StateAlgebra[F], ...
): F[Unit]
```

Функции `Program` не знают, что такое `IO`. Они работают с любым `F`,
у которого есть `Monad[F]` и нужные алгебры. Это позволяет подставить
тестовый интерпретер без изменения бизнес-логики.

### 2. Без монадных трансформеров (`Interpreters.scala`)

Вместо стека `StateT[WriterT[ReaderT[IO, Env, *], Log, *], State, *]` —
три независимых интерпретера, каждый с `Ref[IO, _]`:

```
ConfigInterpreter  — замыкание над PostConfig (аналог Reader, но без ReaderT)
LogInterpreter     — Ref[IO, Vector[String]]  (аналог Writer, но без WriterT)
StateInterpreter   — Ref[IO, PostState]       (аналог State, но без StateT)
```

`Ref` даёт атомарное, потокобезопасное изменение состояния без трансформеров.

### 3. С нестабильными эффектами (`ConsoleInterpreter`, `Program.menu`)

`ConsoleAlgebra[F]` изолирует нестабильные (volatile) эффекты ввода-вывода.
`IO(scala.io.StdIn.readLine())` — каждый вызов даёт новый результат.
`Program.menu` использует её через алгебру, не зная об `IO` напрямую.

---

## Тарифы (PostConfig в Main.scala)

| Параметр            | Значение         |
|---------------------|------------------|
| Тариф по весу       | 50 ₽/кг          |
| Максимальный вес    | 30 кг            |
| Хранение            | 15 ₽/день        |
| Правила упаковки    | Standard, Fragile, Oversized |

## Классы посылок (`packageClass`)

| Класс  | Вес           |
|--------|---------------|
| Light  | до 1 кг       |
| Medium | от 1 до 10 кг |
| Heavy  | свыше 10 кг   |
