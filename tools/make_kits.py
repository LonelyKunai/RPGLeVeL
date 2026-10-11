#!/usr/bin/env python3
"""Genera docs/kits/*.yml (PlayerKits2; formato tomado de un kit real del servidor)."""
import os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "kits")
os.makedirs(OUT, exist_ok=True)
ONCE = 315360000  # 10 anios en segundos: equivale a "una sola vez"
PIECES = ["weapon", "helmet", "chestplate", "leggings", "boots", "offhand_extra"]
CLASSES = [("artificie", "Artífice", "artificer", "&6"), ("bardo", "Bardo", "bard", "&d"), ("brujo", "Brujo", "warlock", "&5"),
           ("cazador", "Cazador", "hunter", "&a"), ("clerigo", "Clérigo", "cleric", "&f"), ("druida", "Druida", "druid", "&2"),
           ("guerrero", "Guerrero", "warrior", "&c"), ("mago", "Mago", "mage", "&b"), ("nigromante", "Nigromante", "necromancer", "&8"),
           ("picaro", "Pícaro", "rogue", "&e")]
# (id, nombre, color, set universal, tier, [(material, cantidad)])
PAID = [("bronce", "Bronce", "&6", "adventurer", 1, [("steel_ingot", 4)]), ("plata", "Plata", "&7", "adventurer", 2, [("steel_ingot", 8)]),
        ("oro", "Oro", "&e", "adventurer", 3, [("steel_ingot", 12), ("cobalt_ingot", 2)]), ("platino", "Platino", "&f", "guardian", 2, [("steel_ingot", 16), ("cobalt_ingot", 4)]),
        ("esmeralda", "Esmeralda", "&a", "guardian", 3, [("cobalt_ingot", 6)]), ("rubi", "Rubí", "&c", "wanderer", 3, [("cobalt_ingot", 8)]),
        ("zafiro", "Zafiro", "&9", "guardian", 4, [("cobalt_ingot", 10), ("sky_ingot", 1)]), ("amatista", "Amatista", "&5", "wanderer", 4, [("cobalt_ingot", 12), ("sky_ingot", 2)]),
        ("diamante", "Diamante", "&b&l", "guardian", 5, [("sky_ingot", 3)]), ("mitico", "Mítico", "&d&l", "wanderer", 5, [("sky_ingot", 4), ("dragon_core", 1)])]
SOUND = '    2:\n      action: "playsound: ENTITY_PLAYER_LEVELUP;10;1"\n'


def kit(name, title, color, cooldown, perm, vanilla, rlv, lore, extra_cmds=(), price=0):
    items = "".join(f"  {i}:\n    id: {m}\n    amount: {a}\n" for i, (m, a) in enumerate(vanilla, 1))
    cmds = [f"rlv give {i} %player% {n}" for i, n in rlv] + list(extra_cmds)
    claim = "".join(f"    {i}:\n      action: \"console_command: {c}\"\n" for i, c in enumerate(cmds, 1))
    n = len(cmds) + 1
    claim += f"    {n}:\n      action: \"playsound: ENTITY_PLAYER_LEVELUP;10;1\"\n"
    lines = "\n".join(f"      - '{l}'" for l in lore)
    hdr = f"      - '&fEste kit incluye:'\n{lines}\n      - ''\n"
    cd = "una sola vez" if cooldown >= ONCE else f"{cooldown // 3600} h"
    return f"""cooldown: {cooldown}
permission_required: {'true' if perm else 'false'}
items:
{items}actions:
  claim:
{claim}  error:
    1:
      action: "playsound: BLOCK_NOTE_BLOCK_PLING;10;0.1"
requirements:
  one_time_requirements: false
  price: {price}
display:
  default:
    id: {title[0]}
    name: '{color}&l{title[1]}'
    amount: 1
    lore:
{hdr}      - '&7Espera: &c{cd}'
      - ''
      - '&aClic izquierdo para reclamar'
    item_flags:
      - HIDE_ATTRIBUTES
  no_permission:
    id: BARRIER
    name: '{color}&l{title[1]}'
    amount: 1
    lore:
{hdr}      - '&cNo tienes permiso para este kit.'
  cooldown:
    id: {title[0]}
    name: '{color}&l{title[1]}'
    amount: 1
    lore:
{hdr}      - '&cDebes esperar &7%time%'
      - '&cpara reclamarlo de nuevo.'
    item_flags:
      - HIDE_ATTRIBUTES
"""


def w(name, txt):
    open(os.path.join(OUT, name + ".yml"), "w", encoding="utf-8").write(txt)


w("inicio", kit("inicio", ("BREAD", "Kit Inicial"), "&a", ONCE, False,
                [("BREAD", 16), ("TORCH", 32), ("STONE_PICKAXE", 1), ("STONE_AXE", 1)], [],
                ["&8- &7x16 Pan, x32 Antorchas", "&8- &7Pico y hacha de piedra"]))
w("diario", kit("diario", ("GOLD_NUGGET", "Kit Diario"), "&e", 86400, False,
                [("BREAD", 8), ("IRON_NUGGET", 8)], [], ["&8- &7x8 Pan, x8 Pepitas de hierro", "&8- &7100 monedas"],
                ["rlv coins give %player% 100"]))
for cid, name, p, col in CLASSES:
    w(f"clase_{cid}", kit(cid, ("IRON_SWORD", f"Kit de {name}"), col, ONCE, True,
                         [("BREAD", 8)], [(f"{p}_{x}_t1", 1) for x in PIECES],
                         [f"&8- &7Set nivel I de {name} (arma, armadura y mano secundaria)", "&8- &7Solo lo usas si eres de esa clase"]))
for pid, name, col, s, t, mats in PAID:
    rl = [(f"{s}_{x}_t{t}", 1) for x in PIECES[:5]] + mats
    w(f"vip_{pid}", kit(pid, ("NETHER_STAR", f"Kit {name}"), col, 168 * 3600, True,
                       [("GOLDEN_APPLE", 2)], rl,
                       [f"&8- &7Set {s} nivel {t} (arma y armadura)", "&8- &7" + ", ".join(f"x{n} {m}" for m, n in mats)]))
print("OK", len(os.listdir(OUT)))
