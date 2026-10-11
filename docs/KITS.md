# Kits (PlayerKits2)

**Archivos listos:** `docs/kits/*.yml` (22 kits, formato tomado de un kit real del servidor). Cópialos a `plugins/PlayerKits2/kits/` y ejecuta `/pk reload` (o reinicia). Regenerar: `python3 tools/make_kits.py`.
- "Una sola vez" = cooldown de 10 años (no usé claves no verificadas de PlayerKits2).
- Los kits de clase/pago usan `permission_required: true` (nodo `playerkits.kit.<kit>`, ya repartido por `ranks.txt`). `inicio` y `diario` son para todos.
- Los ítems RPG se entregan con `rlv give <id> %player% <n>` por consola; cada kit trae además un ítem vanilla en `items:`.
- Los kits no abren menú: el jugador usa `/kit` o el menú de PlayerKits2 que ya tengas.

## Diseño

Cada kit entrega ítems de RPGLeVeL con `rlv give <item_id> <jugador> [cantidad]` (consola; usa `%player%` en la acción del kit) y objetos vanilla con `give`. Permiso de cada kit: `playerkits.kit.<kit>` (nodo por defecto de PlayerKits2), que ya reparten los grupos de `docs/luckperms/ranks.txt`.

## Gratuitos
| Kit | Permiso | Cooldown | Una vez | Contenido |
|---|---|---|---|---|
| inicio | (todos) | - | sí | `give %player% bread 16`, `give %player% torch 32`, `give %player% stone_pickaxe 1`, `give %player% stone_axe 1` |
| diario | (todos) | 24 h | no | `give %player% bread 8`, `give %player% iron_nugget 8` + 100 monedas (`rlv coins give %player% 100`) |
| clase_<id> | grupo `class_<id>` | - | sí | Set nivel I de la clase: `<p>_weapon_t1`, `<p>_helmet_t1`, `<p>_chestplate_t1`, `<p>_leggings_t1`, `<p>_boots_t1`, `<p>_offhand_extra_t1` |

Prefijo `<p>` por clase: artificie=`artificer`, bardo=`bard`, brujo=`warlock`, cazador=`hunter`, clerigo=`cleric`, druida=`druid`, guerrero=`warrior`, mago=`mage`, nigromante=`necromancer`, picaro=`rogue`.
Los kits de clase son de una sola vez cada uno (se pueden reclamar los de otras clases si cambias; el cambio de clase ya cuesta monedas y espera 6 h).

## De pago (semanales, 168 h)
Cada escalón entrega el set universal de su nivel + materiales de forja. Como los grupos se heredan, el jugador ve también los kits de escalones inferiores.

| Kit | Grupo | Contenido |
|---|---|---|
| vip_bronce | vip_bronce | `adventurer_weapon_t1`, `adventurer_helmet_t1`, `adventurer_chestplate_t1`, `adventurer_leggings_t1`, `adventurer_boots_t1`, `steel_ingot` x4 |
| vip_plata | vip_plata | pieza `adventurer_*_t2` (arma + 4 piezas), `steel_ingot` x8 |
| vip_oro | vip_oro | `adventurer_*_t3`, `steel_ingot` x12, `cobalt_ingot` x2 |
| vip_platino | vip_platino | `guardian_*_t2`, `steel_ingot` x16, `cobalt_ingot` x4 |
| vip_esmeralda | vip_esmeralda | `guardian_*_t3`, `cobalt_ingot` x6 |
| vip_rubi | vip_rubi | `wanderer_*_t3`, `cobalt_ingot` x8 |
| vip_zafiro | vip_zafiro | `guardian_*_t4`, `cobalt_ingot` x10, `sky_ingot` x1 |
| vip_amatista | vip_amatista | `wanderer_*_t4`, `cobalt_ingot` x12, `sky_ingot` x2 |
| vip_diamante | vip_diamante | `guardian_*_t5`, `sky_ingot` x3 |
| vip_mitico | vip_mitico | `wanderer_*_t5`, `sky_ingot` x4, `dragon_core` x1 |

`<set>_*_tN` = `weapon`, `helmet`, `chestplate`, `leggings`, `boots`. Los ids existen en `data/items/universal/` (`adventurer`, `guardian`, `wanderer`) y `data/items/materials/`.
Todo es comodidad (no sustituye al progreso: los ítems tienen requisitos de nivel de clase) y los materiales entran en el precio de la tienda, por eso las cantidades son bajas.
