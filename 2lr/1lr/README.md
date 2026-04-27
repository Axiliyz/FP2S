# ЛР1: Монады — Вариант 15. Почтовое отделение

## Структура проекта

```
src/main/scala/
├── monads/              # Блок 0 — инфраструктура монад
│   ├── Monad.scala      # trait Monad[M[_]]
│   ├── Reader.scala     # Reader[Env, A]
│   ├── Writer.scala     # Writer[Log, A] + Monoid
│   ├── State.scala      # State[S, A]
│   └── IO.scala         # IO[A] с unsafeRun
└── postoffice/          # предметная область
    ├── Domain.scala     # типы: PostOfficeConfig, Parcel, PostOfficeState
    ├── ReaderOps.scala  # Блок 1 — Reader (тарифы, вес, хранение, класс)
    ├── WriterOps.scala  # Блок 2 — Writer (логирование операций)
    ├── StateOps.scala   # Блок 3 — State (очередь, приём, выдача, дни)
    └── App.scala        # Блок 4 — IO-сценарий + main
```

## Запуск

```bash
sbt run
```

## Использованные монады

| Монада   | Назначение                                     |
|----------|------------------------------------------------|
| `Reader` | Конфигурация: тариф, макс. вес, хранение       |
| `Writer` | Логирование: приём, тариф, хранение, выдача     |
| `State`  | Состояние: очередь, посылки, выручка, день       |
| `IO`     | Консольное взаимодействие с пользователем        |
