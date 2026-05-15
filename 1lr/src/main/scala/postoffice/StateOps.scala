package postoffice

import monads.State

object StateOps:

  // Добавляет клиента в очередь, возвращает его номер в ней.
  def takeQueueNumber(clientName: String): State[PostOfficeState, Int] =
    State { st =>
      // :+ добавляет в конец списка. Создаёт новый List, не мутирует старый.
      val newQueue = st.queue :+ clientName
      // copy создаёт новый PostOfficeState с изменённым полем queue.
      // newQueue.length — номер в очереди (значение A).
      (st.copy(queue = newQueue), newQueue.length)
    }

  // Принимает посылку: создаёт Parcel, обновляет состояние, возвращает созданную посылку.
  def acceptParcel(
      sender:    String,
      recipient: String,
      weight:    Double,
      cost:      Double
  ): State[PostOfficeState, Parcel] =
    State { st =>
      // id берётся из состояния (nextId), acceptedDay — текущий день из состояния.
      val parcel = Parcel(
        id          = st.nextId,
        sender      = sender,
        recipient   = recipient,
        weight      = weight,
        acceptedDay = st.currentDay
      )
      val newState = st.copy(
        // filterNot убирает отправителя из очереди. Создаёт новый список.
        queue           = st.queue.filterNot(_ == sender),
        // Map + (key -> value) создаёт новую Map с добавленной парой.
        acceptedParcels = st.acceptedParcels + (parcel.id -> parcel),
        revenue         = st.revenue + cost,
        // Увеличиваем счётчик id для следующей посылки.
        nextId          = st.nextId + 1
      )
      // Возвращаем пару: новое состояние и созданная посылка.
      (newState, parcel)
    }

  // Выдаёт посылку по id: убирает из хранилища, добавляет в выданные.
  // Возвращает Option[Parcel] — Some если нашли, None если нет.
  def issueParcel(
      parcelId:    Int,
      storageCost: Double
  ): State[PostOfficeState, Option[Parcel]] =
    State { st =>
      // Map.get возвращает Option[Parcel].
      st.acceptedParcels.get(parcelId) match
        case Some(parcel) =>
          val newState = st.copy(
            // Map - key создаёт новую Map без данного ключа.
            acceptedParcels = st.acceptedParcels - parcelId,
            // :+ добавляет посылку в список выданных.
            issuedParcels   = st.issuedParcels :+ parcel,
            revenue         = st.revenue + storageCost
          )
          (newState, Some(parcel))
        case None =>
          // Посылки нет — состояние не меняется, возвращаем None.
          (st, None)
    }

  // val, не def — State без параметров. Увеличивает день на 1, возвращает новый номер дня.
  val nextDay: State[PostOfficeState, Int] =
    State { st =>
      val newDay = st.currentDay + 1
      (st.copy(currentDay = newDay), newDay)
    }
