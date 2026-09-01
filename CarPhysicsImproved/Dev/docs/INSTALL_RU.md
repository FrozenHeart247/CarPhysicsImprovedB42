# Установка Roadcraft Dynamics 0.4.4-dev

## Требования

- Project Zomboid Build 42; точно проверенный ABI — `42.20.4 b0bbce05d5`.
- ZombieBuddy `2.3.2` или новее.
- Roadcraft Dynamics и ZombieBuddy должны быть включены в одном наборе модов.

## Обычная Workshop-установка

1. Установите и запустите ZombieBuddy его штатным способом.
2. Подпишитесь на Roadcraft Dynamics и включите мод.
3. При первом обнаружении нового Java JAR подтвердите его загрузку в интерфейсе ZombieBuddy.
4. Полностью перезапустите игру. Одна перезагрузка сохранения недостаточна для изоляции Java-патчей.

Steam доставляет JAR автоматически из:

```text
mods/RoadcraftDynamicsB42/42/media/java/roadcraft-dynamics.jar
```

Копировать файлы Roadcraft в корень игры больше не нужно.

## Обязательная очистка старой версии 0.2.x

Перед первым запуском ZB-версии закройте игру и удалите только старые файлы Roadcraft, ранее скопированные в корень Project Zomboid:

```text
<GameDirectory>/zombie/core/physics/CarController.class
<GameDirectory>/zombie/roadcraft/**
```

Если сохранён старый `SHA256SUMS.txt`, используйте его как точный список. Не удаляйте всю папку `<GameDirectory>/zombie`: там могут быть файлы других модов. После очистки ванильный `CarController` должен загружаться из `projectzomboid.jar`, а ZombieBuddy применит к нему точечный patch.

## Проверка загрузки

После полного старта найдите в `console.txt`:

```text
[RoadcraftDynamics] ZombieBuddy runtime 0.4.4-dev loaded.
[RoadcraftDynamics] Sandbox configuration applied; runtime status=ACTIVE
```

При первом управляемом автомобиле ожидается строка `Controller active`. При ручном переключении выводится `Manual shift`, при пробуксовке — `Burnout active`.

`ACTIVE` подтверждает загрузку JAR и конфигурации, но не доказывает правильность физики. Нужно отдельно проверить запуск двигателя, качение, передачи, burnout, столкновения и буксировку.

## Dedicated server

ZombieBuddy и Roadcraft должны быть установлены и разрешены на сервере и клиентах. JAR лежит прямо в `media/java`, поэтому ZB не отфильтровывает его как client-only или server-only. Сервер использует свою политику одобрения Java-модов; точная настройка зависит от конфигурации ZombieBuddy.

Roadcraft не вводит собственную синхронизацию машин. Команды применяются только процессом с локальным physics authority, после чего используются штатные сетевые механизмы PZ.

## Обновление игры

Полная подмена классов больше не используется, поэтому несовпадение ABI не должно приводить к старому silent crash из-за loose `CarController`. Однако ZB-патч может не примениться либо runtime adapter может отключить hooks. После любого обновления Build 42:

1. полностью перезапустите игру;
2. проверьте строки ZombieBuddy и Roadcraft в свежем `console.txt`;
3. выполните короткую SP-проверку;
4. только затем проверяйте listen/dedicated MP.

## Удаление

Отключите Roadcraft Dynamics и отпишитесь от Workshop item. JAR не копируется в корень игры, поэтому отдельная ручная очистка для версии 0.4.x не нужна. ZombieBuddy можно оставить для других Java-модов.
