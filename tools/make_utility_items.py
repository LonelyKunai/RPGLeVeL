#!/usr/bin/env python3
"""Genera los objetos de utilidad: data/items/utility/utility.rlvp (los ítems) y data/utility/utility.yml (su comportamiento)."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources/data")
CMD = 914000

RIGHT = "§7Usa §eclic derecho §7para usarlo."
items = []  # dict(id, mat, name, lore, type, cfg, tier, recipe)


def add(id, mat, name, lore, type, cfg=None, tier=0, recipe=None, station="adventurer"):
    items.append(dict(id=id, mat=mat, name=name, lore=lore, type=type, cfg=cfg or {}, tier=tier, recipe=recipe, station=station))


# ---- estaciones portátiles (vanilla): clic derecho y se abren sin colocarlas ----
V = [("workbench", "CRAFTING_TABLE", "Mesa de Crafteo Portátil", "una mesa de crafteo", 1, ["CRAFTING_TABLE:1", "ENDER_PEARL:1", "IRON_INGOT:4"]),
     ("anvil", "ANVIL", "Yunque Portátil", "un yunque", 2, ["ANVIL:1", "ENDER_PEARL:1", "steel_ingot:2"]),
     ("smithing", "SMITHING_TABLE", "Mesa de Herrería Portátil", "una mesa de herrería", 2, ["SMITHING_TABLE:1", "ENDER_PEARL:1", "steel_ingot:2"]),
     ("grindstone", "GRINDSTONE", "Afilador Portátil", "una piedra de afilar", 2, ["GRINDSTONE:1", "ENDER_PEARL:1", "IRON_INGOT:4"]),
     ("stonecutter", "STONECUTTER", "Cortapiedras Portátil", "un cortapiedras", 2, ["STONECUTTER:1", "ENDER_PEARL:1", "IRON_INGOT:4"]),
     ("loom", "LOOM", "Telar Portátil", "un telar", 2, ["LOOM:1", "ENDER_PEARL:1", "STRING:8"]),
     ("cartography", "CARTOGRAPHY_TABLE", "Mesa de Cartografía Portátil", "una mesa de cartografía", 2, ["CARTOGRAPHY_TABLE:1", "ENDER_PEARL:1", "PAPER:8"]),
     ("enchanting", "ENCHANTING_TABLE", "Mesa de Encantamientos Portátil", "una mesa de encantamientos (sin estanterías: hasta nivel 8)", 3, ["ENCHANTING_TABLE:1", "ENDER_PEARL:2", "cobalt_ingot:1"])]
for k, mat, name, what, tier, rec in V:
    add(f"util_{k}", mat, f"§b§l{name}", [f"§7Abre {what} sin colocarla.", RIGHT], "station", {"arg": k}, tier, rec)

# ---- estaciones de forja RPG portátiles ----
R = [("class", "Estación de Clase Portátil", "la estación de tu §fclase§7", 3),
     ("subclass", "Estación de Subclase Portátil", "la estación de tu §fsubclase§7", 4),
     ("adventurer", "Estación de Aventurero Portátil", "la estación del set §fAventurero§7", 3),
     ("wanderer", "Estación de Caminante Portátil", "la estación del set §fCaminante§7", 3),
     ("guardian", "Estación de Guardián Portátil", "la estación del set §fGuardián§7", 3)]
for k, name, what, tier in R:
    mat = "SMITHING_TABLE" if k == "class" else "CARTOGRAPHY_TABLE" if k == "subclass" else "FLETCHING_TABLE"
    rec = ["ENDER_PEARL:2", "steel_ingot:4", "cobalt_ingot:2"] if tier == 3 else ["ENDER_PEARL:2", "steel_ingot:6", "cobalt_ingot:4"]
    add(f"util_station_{k}", mat, f"§6§l{name}", [f"§7Abre {what} sin colocarla.", "§7Se necesita la clase / subclase correspondiente para forjar.", RIGHT],
        "rpg_station", {"arg": k}, tier, rec)

# ---- mochilas mejorables ----
BAGS = [(1, 9, "Mochila de Cuero", "§6"), (2, 18, "Mochila Reforzada", "§f"), (3, 27, "Mochila de Acero", "§b"),
        (4, 36, "Mochila de Cobalto", "§9"), (5, 54, "Mochila del Cielo", "§d")]
for t, slots, name, col in BAGS:
    lore = [f"§7Capacidad: §f{slots} §7espacios.", "§7Clic derecho para abrirla (también clic derecho sobre ella en el inventario).",
            "§8No admite mochilas ni shulkers dentro."]
    lore.append("§7Mejórala con un §eKit de Mejora §7de nivel " + str(t + 1) + "." if t < 5 else "§aNivel máximo.")
    rec = ["LEATHER:8", "STRING:4", "IRON_INGOT:2"] if t == 1 else None
    add(f"util_backpack_t{t}", "LEATHER_HORSE_ARMOR", f"{col}§l{name}", lore, "backpack", {"tier": t, "slots": slots}, t, rec)
KITS = {2: ["IRON_INGOT:8", "LEATHER:8", "steel_ingot:2"], 3: ["steel_ingot:8", "LEATHER:12", "cobalt_ingot:1"],
        4: ["cobalt_ingot:6", "steel_ingot:8", "LEATHER:16"], 5: ["sky_ingot:4", "cobalt_ingot:8", "dragon_core:1"]}
for t, rec in KITS.items():
    name = BAGS[t - 1][2]
    add(f"util_backpack_kit_t{t}", "NETHERITE_UPGRADE_SMITHING_TEMPLATE", f"§e§lKit de Mejora: {name}",
        [f"§7Mejora una §fMochila nivel {t - 1} §7a §f{name}§7 (§f{BAGS[t - 1][1]} §7espacios).",
         "§7El contenido se conserva.", "§eUso: §7con el kit en el cursor, haz clic sobre la mochila en tu inventario;",
         "§7o clic derecho con el kit en una mano y la mochila en la otra."], "backpack_kit", {"tier": t}, t, rec)

# ---- otros ----
add("util_ender_pouch", "ENDER_CHEST", "§5§lBolsa de Ender", ["§7Abre tu cofre de ender sin colocarlo.", RIGHT], "ender", {}, 2, ["ENDER_CHEST:1", "LEATHER:4", "ENDER_PEARL:2"])
add("util_trash_bag", "CAULDRON", "§8§lBolsa de Basura", ["§7Abre un contenedor para tirar objetos.", "§c¡Lo que dejes dentro se destruye al cerrar!", RIGHT], "trash", {}, 1, ["LEATHER:4", "STRING:2"])
add("util_repair_kit", "PRISMARINE_SHARD", "§a§lKit de Reparación", ["§7Repara un §f40% §7de la durabilidad de tu armadura equipada",
    "§7y del objeto de la mano secundaria. Se consume.", RIGHT], "repair", {"percent": 40}, 2, ["IRON_INGOT:6", "steel_ingot:1"])
add("util_repair_kit_big", "PRISMARINE_CRYSTALS", "§a§lKit de Reparación Superior", ["§7Repara el §f100% §7de la durabilidad de tu armadura equipada",
    "§7y del objeto de la mano secundaria. Se consume.", RIGHT], "repair", {"percent": 100}, 4, ["steel_ingot:6", "cobalt_ingot:2"])
add("util_recall_scroll", "PAPER", "§d§lPergamino de Regreso", ["§7Te teletransporta a tu cama (o al spawn) tras §f5 s §7sin moverte.", "§7Se consume al teletransportar.", RIGHT],
    "recall", {"warmup": 5, "cooldown": 30}, 1, ["PAPER:2", "ENDER_PEARL:1"])
for k, mat, name, amount in [("s", "GOLD_NUGGET", "Bolsa de Monedas Pequeña", 500), ("m", "GOLD_INGOT", "Bolsa de Monedas Mediana", 5000), ("l", "RAW_GOLD", "Bolsa de Monedas Grande", 50000)]:
    add(f"util_coin_pouch_{k}", mat, f"§6§l{name}", [f"§7Contiene §e{amount} §7monedas.", RIGHT, "§8Se consume al usarla."], "coins", {"amount": amount}, 0)
for k, mat, name, amount in [("s", "BOOK", "Tomo de Experiencia Menor", 500), ("m", "ENCHANTED_BOOK", "Tomo de Experiencia", 3000), ("l", "KNOWLEDGE_BOOK", "Tomo de Experiencia Mayor", 20000)]:
    add(f"util_xp_tome_{k}", mat, f"§3§l{name}", [f"§7Otorga §b{amount} XP §7a tu clase activa.", RIGHT, "§8Se consume al usarlo."], "xp", {"amount": amount}, 0)


def q(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


os.makedirs(os.path.join(RES, "items/utility"), exist_ok=True)
os.makedirs(os.path.join(RES, "utility"), exist_ok=True)
o = ["items:"]
for i, it in enumerate(items):
    o += [f"  {it['id']}:", f"    material: {it['mat']}", f"    custom_model_data: {CMD + 1 + i}", f"    display_name: {q(it['name'])}", "    lore:"]
    o += [f"    - {q(l)}" for l in it["lore"]]
    o += ["    list:", "      category: utility", '      name: "Utilidades"', "      group: utility", "      type: utility", f"      piece: {it['type']}", f"      tier: {it['tier']}"]
    if it["recipe"]:
        o += ["    recipe:", f"      station: {it['station']}", f"      tier: {it['tier']}", "      ingredients:"] + [f'      - "{x}"' for x in it["recipe"]]
open(os.path.join(RES, "items/utility/utility.rlvp"), "w", encoding="utf-8").write("\n".join(o) + "\n")

y = ["# Comportamiento de los objetos de utilidad (data/items/utility/utility.rlvp). Se edita aquí; /rlv reload lo recarga.",
     "# type: station (arg: workbench|anvil|smithing|grindstone|stonecutter|loom|cartography|enchanting) | rpg_station (arg: class|subclass|<id de estación>)",
     "#       backpack (tier, slots) | backpack_kit (tier) | ender | trash | repair (percent) | recall (warmup s, cooldown s) | coins (amount) | xp (amount)",
     "settings:",
     "  block_shulkers_in_backpack: true   # las mochilas nunca admiten otras mochilas; true añade los shulkers",
     "  autosave_seconds: 60               # guarda las mochilas abiertas cada N segundos (también al cerrar y al apagar)",
     "items:"]
for it in items:
    y.append(f"  {it['id']}:")
    y.append(f"    type: {it['type']}")
    for k, v in it["cfg"].items():
        y.append(f"    {k}: {v}")
open(os.path.join(RES, "utility/utility.yml"), "w", encoding="utf-8").write("\n".join(y) + "\n")
print("OK", len(items), "objetos")
