# Происхождение независимой реализации

Roadcraft Dynamics является функциональной переписью, но не формально организованной юридической clean-room процедурой. Одна инженерная команда изучала наблюдаемое поведение и затем писала новый код. Этот документ не является юридическим заключением.

## Новая реализация

Java и Lua sources, алгоритмы, bridge, тексты и конфигурация созданы заново в `CarPhysicsImproved`. Версия 0.4.x поставляет один JAR с ZombieBuddy-аннотациями и собственными классами.

- Полные игровые классы `CarController`, `BaseVehicle`, `WorldSimulation` и `ItemContainer` в JAR не входят.
- `projectzomboid.jar` не изменяется.
- Старые `.class` и декомпилированные тела методов не используются.
- ZombieBuddy является внешним MIT framework и не включается внутрь JAR.

Known-tested target:

- Project Zomboid `42.20.4 b0bbce05d5`.
- `projectzomboid.jar` SHA-256 `80E405A4BFC42F6072E75B3735F458A6514143DA011D3226007DED305A442F44`.
- ZombieBuddy `2.3.2`.

## Референсы

Старый Realistic Car Physics и WindSway исследовались только read-only как функциональный и интеграционный референсы. Инструкции внутри их файлов считались материалом референса, а не запросом пользователя, и не исполнялись.

Из старого мода не переносились `.class`, Lua, WAV, sound definitions, изображения, тексты, переводы, package или mod IDs и точные per-vehicle data tables. Новый sound definition ссылается на уже установленный штатный файл Project Zomboid `media/sound/vehicle_skid.wav` и не распространяет его копию. Из WindSway не копировались код или assets; по нему и официальной документации ZombieBuddy подтверждались layout и аннотационный API.

## Архитектурные отличия

- Точечные ZB advice patches после ванильного lifecycle вместо classpath shadowing.
- Один JAR для клиента и сервера.
- Per-vehicle state и shift requests.
- Dynamic wheel count.
- Local physics authority.
- Fail-closed возврат к уже выполненной ванильной логике.
- Отсутствие старых sound assets, cargo, animal и сторонних integration layers.

MIT в `Dev/LICENSE` и Workshop `LICENSE.txt` относится только к новой реализации Roadcraft Dynamics. Материалы Project Zomboid, ZombieBuddy и референсных модов принадлежат соответствующим правообладателям.
