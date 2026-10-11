# Objetos de utilidad

33 ítems de uso general (`/rlv give <id> <jugador>` o `/rlv itemlist` → categoría **Utilidades**). Todos funcionan con **clic derecho**, sin colocarlos. Permiso: `rpgleveltier.utility` (por defecto todos). Se fabrican en la estación de **Aventurero** (recetas en `data/items/utility/utility.rlvp`); las bolsas de monedas y los tomos son para recompensas/admin.
Comportamiento editable en `data/utility/utility.yml` (`/rlv reload`).

| Grupo | Ítems | Qué hace |
|---|---|---|
| Estaciones vanilla portátiles | `util_workbench`, `util_anvil`, `util_smithing`, `util_grindstone`, `util_stonecutter`, `util_loom`, `util_cartography`, `util_enchanting` | Abre la estación al instante (la de encantamientos sin estanterías: hasta nivel 8). |
| Estaciones RPG portátiles | `util_station_class`, `util_station_subclass`, `util_station_adventurer`, `util_station_wanderer`, `util_station_guardian` | Abre el menú de forja de tu clase / subclase / set universal. |
| Mochilas | `util_backpack_t1`…`t5` (9 / 18 / 27 / 36 / 54 espacios) | Clic derecho (en la mano o sobre ella en el inventario) abre el contenido. No admite mochilas ni shulkers. |
| Kits de mejora | `util_backpack_kit_t2`…`t5` | Mejoran la mochila al siguiente nivel **conservando el contenido** (ver abajo). |
| Bolsa de Ender | `util_ender_pouch` | Abre tu cofre de ender. |
| Bolsa de Basura | `util_trash_bag` | Contenedor que destruye lo que dejes dentro al cerrarlo. |
| Kits de reparación | `util_repair_kit` (40%), `util_repair_kit_big` (100%) | Reparan la armadura equipada y el objeto de la otra mano. Se consumen. |
| Pergamino de Regreso | `util_recall_scroll` | Teletransporta a tu cama (o al spawn) tras 5 s sin moverte ni recibir daño; enfriamiento 30 s. |
| Bolsas de monedas | `util_coin_pouch_s/m/l` (500 / 5000 / 50000) | Ingresan monedas. Se consumen. |
| Tomos de experiencia | `util_xp_tome_s/m/l` (500 / 3000 / 20000) | Dan XP a tu clase activa. Se consumen. |

## Mejorar la mochila
Niveles: Cuero (9) → Reforzada (18) → Acero (27) → Cobalto (36) → Cielo (54). Cada kit mejora **un** nivel (el kit nivel N sirve para la mochila nivel N-1).
- **Con el cursor:** coge el kit, haz clic sobre la mochila en tu inventario.
- **Con las manos:** kit en una mano y mochila en la otra, clic derecho.
Recetas (estación Aventurero): mochila nivel 1 = 8 cuero + 4 hilo + 2 hierro; kit 2 = 8 hierro + 8 cuero + 2 acero; kit 3 = 8 acero + 12 cuero + 1 cobalto; kit 4 = 6 cobalto + 8 acero + 16 cuero; kit 5 = 4 cielo + 8 cobalto + 1 núcleo de dragón.

## Notas técnicas
- El contenido vive en `plugins/RPGLeVeL/backpacks/<uuid>.yml`; el ítem solo lleva el uuid. Si se destruye el ítem, el archivo queda (recuperable por un admin). Se guarda al cerrar, cada 60 s y al apagar.
- Una mochila no se puede abrir dos veces a la vez, ni mejorar mientras está abierta.
- Las estaciones vanilla usan la API de Paper/Purpur (con respaldo de inventario virtual en Spigot).
- Regenerar los ítems: `python3 tools/make_utility_items.py`.
