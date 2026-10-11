import os
import yaml

# Representador personalizado para evitar comillas innecesarias en YAML
class UnquotedDumper(yaml.SafeDumper):
    pass

def str_representer(dumper, data):
    if data.startswith("§") or " " in data or ":" in data or "\n" in data:
        return dumper.represent_scalar('tag:yaml.org,2002:str', data, style='"')
    return dumper.represent_scalar('tag:yaml.org,2002:str', data, style='')

UnquotedDumper.add_representer(str, str_representer)

# Mapeo de rareza según la Tier
TIER_RARITY = {
    1: "común",
    2: "poco común",
    3: "raro",
    4: "épico",
    5: "legendario"
}

# Materiales de mejora de Tier (T2 a T5)
TIER_MATERIALS = {
    2: "steel_ingot",
    3: "cobalt_ingot",
    4: "sky_ingot",
    5: "dragon_core"
}

# Unidades de material por pieza (cantidad que cuesta mejorarla)
PIECE_UNITS = {"weapon": 2, "helmet": 3, "chestplate": 4, "leggings": 3, "boots": 2, "offhand_extra": 1}

PIECE_TRANSLATIONS = {
    "weapon": "Arma Principal",
    "helmet": "Casco",
    "chestplate": "Pechera",
    "leggings": "Pantalones",
    "boots": "Botas",
    "offhand_extra": "Objeto Secundario"
}

LORE_DESCRIPTIONS = {
    1: "§7Equipo inicial básico forjado con materiales estándar.",
    2: "§7Reforzado con minerales raros para resistir combate avanzado.",
    3: "§7Imbuido con energía elemental y componentes de gran valor.",
    4: "§7Una reliquia poderosa imbuida con la esencia de los cielos.",
    5: "§7Un artefacto legendario impregnado con el poder del Dragón y el Cosmos."
}

CLASSES = {
    "warrior": {"display": "Guerrero", "base_arm": "IRON", "weapon": "IRON_SWORD", "extra": "SHIELD"},
    "mage": {"display": "Mago", "base_arm": "LEATHER", "weapon": "BLAZE_ROD", "extra": "BOOK"},
    "hunter": {"display": "Cazador", "base_arm": "LEATHER", "weapon": "BOW", "extra": "ARROW"},
    "rogue": {"display": "Pícaro", "base_arm": "LEATHER", "weapon": "IRON_SWORD", "extra": "IRON_NUGGET"},
    "cleric": {"display": "Clérigo", "base_arm": "CHAINMAIL", "weapon": "MACE", "extra": "GLOWSTONE_DUST"},
    "necromancer": {"display": "Nigromante", "base_arm": "LEATHER", "weapon": "STICK", "extra": "BONE"},
    "druid": {"display": "Druida", "base_arm": "LEATHER", "weapon": "WOODEN_AXE", "extra": "WOODEN_HOE"},
    "artificer": {"display": "Artífice", "base_arm": "IRON", "weapon": "CROSSBOW", "extra": "REDSTONE"},
    "bard": {"display": "Bardo", "base_arm": "LEATHER", "weapon": "GOLDEN_SWORD", "extra": "GOAT_HORN"},
    "warlock": {"display": "Brujo", "base_arm": "LEATHER", "weapon": "IRON_SWORD", "extra": "ENDER_PEARL"}
}

SUBCLASSES = {
    "paladin": "Paladín", "barbarian": "Bárbaro", "death_knight": "Caballero de la Muerte",
    "pyromancer": "Piroamante", "cryomancer": "Criomante", "electromancer": "Electromante",
    "marksman": "Tirador", "beastmaster": "Maestro de Bestias", "trapper": "Trampero",
    "assassin": "Asesino", "venomancer": "Envenenador", "shadow_duelist": "Sombra / Duelista",
    "priest_of_light": "Sacerdote de Luz", "inquisitor": "Inquisidor", "monk": "Monje",
    "shadow_lord": "Señor de las Sombras", "reaper": "Cosechador", "plaguer": "Plagador",
    "wild_guardian": "Guardián Salvaje", "apex_hunter": "Cazador Depredador", "earth_shaman": "Chamán Terrestre",
    "alchemist": "Maestro Alquimista", "engineer": "Ingeniero", "demolitionist": "Demoledor",
    "valor_herald": "Ladrador del Valor", "discord_master": "Maestro de la Discordia", "minstrel": "Menestral",
    "demon_summoner": "Invocador Demoníaco", "cursed": "Maldito", "soul_eater": "Devorador de Almas"
}

UNIVERSAL_SETS = {
    "adventurer": "Aventurero",
    "wanderer": "Caminante",
    "guardian": "Guardián"
}

# Cada ítem recibe un custom_model_data único y estable (orden de generación)
CMD_BASE = 910000
_cmd_counter = [CMD_BASE]

def next_cmd():
    _cmd_counter[0] += 1
    return _cmd_counter[0]

PIECES = ["weapon", "helmet", "chestplate", "leggings", "boots", "offhand_extra"]

def get_piece_material(piece_name, base_mat):
    if piece_name in ["helmet", "chestplate", "leggings", "boots"]:
        return f"{base_mat}_{piece_name.upper()}"
    return base_mat


# --- progresion del equipo: requisitos, estadisticas base, sockets ---------------------------
TIER_LEVELS = {1: 1, 2: 5, 3: 12, 4: 20, 5: 30}          # nivel de clase/subclase para equipar (igual que stations.tier_levels)
TIER_FACTOR = {1: 1.0, 2: 1.8, 3: 3.0, 4: 4.5, 5: 6.5}   # escala de estadisticas por tier
SOCKETS_BY_TIER = {1: 0, 2: 1, 3: 1, 4: 2, 5: 3}
ARMOR_PIECE_FACTOR = {"helmet": 0.7, "chestplate": 1.3, "leggings": 1.0, "boots": 0.7}
# (estadistica, valor base por tier 1) del arma y de cada pieza de armadura de cada clase
WEAPON_PROFILE = {
    "warrior": [("attack_damage", 0.6), ("crit_damage", 3)], "mage": [("spell_power", 4), ("mana_max", 10)],
    "hunter": [("crit_chance", 1.5), ("attack_damage", 0.4)], "rogue": [("crit_chance", 2), ("crit_damage", 4)],
    "cleric": [("spell_power", 3), ("mana_regen", 0.3)], "necromancer": [("lifesteal", 1), ("spell_power", 3)],
    "druid": [("mana_regen", 0.4), ("spell_power", 3)], "artificer": [("penetration", 2), ("crit_chance", 1)],
    "bard": [("cooldown_reduction", 1.5), ("mana_regen", 0.3)], "warlock": [("spell_power", 4), ("lifesteal", 0.8)],
}
ARMOR_PROFILE = {
    "warrior": [("damage_reduction", 0.8), ("max_health", 1.0)], "mage": [("mana_max", 8), ("cooldown_reduction", 0.8)],
    "hunter": [("evasion", 0.8), ("max_health", 0.8)], "rogue": [("evasion", 1.2), ("movement_speed", 0.002)],
    "cleric": [("max_health", 1.2), ("mana_regen", 0.2)], "necromancer": [("max_health", 0.8), ("lifesteal", 0.5)],
    "druid": [("max_health", 1.0), ("mana_regen", 0.2)], "artificer": [("armor", 0.6), ("penetration", 1.0)],
    "bard": [("cooldown_reduction", 0.8), ("movement_speed", 0.002)], "warlock": [("mana_max", 8), ("lifesteal", 0.5)],
}
# Estadisticas secundarias (nuevas) por clase: arma y armadura
WEAPON_EXTRA = {
    "warrior": [("attack_speed", 0.01), ("stun_chance", 0.8), ("execute", 1.5)],
    "mage": [("freeze_chance", 0.8), ("mana_on_hit", 0.5)],
    "hunter": [("execute", 1.5), ("bleed_chance", 1.0)],
    "rogue": [("bleed_chance", 1.2), ("attack_speed", 0.01)],
    "cleric": [("stun_chance", 0.6), ("health_regen", 0.05)],
    "necromancer": [("poison_chance", 1.0), ("mana_on_hit", 0.4)],
    "druid": [("poison_chance", 0.8), ("health_regen", 0.05)],
    "artificer": [("shock_chance", 0.8), ("execute", 1.0)],
    "bard": [("mana_on_hit", 0.5), ("shock_chance", 0.6)],
    "warlock": [("burn_chance", 1.0), ("mana_on_hit", 0.4)],
}
ARMOR_EXTRA = {
    "warrior": [("thorns", 1.0), ("knockback_resistance", 0.02)],
    "mage": [("status_resist", 1.0), ("health_regen", 0.03)],
    "hunter": [("luck", 0.2), ("status_resist", 0.8)],
    "rogue": [("luck", 0.2), ("xp_bonus", 0.5)],
    "cleric": [("status_resist", 1.0), ("health_regen", 0.05)],
    "necromancer": [("thorns", 0.8), ("health_regen", 0.03)],
    "druid": [("health_regen", 0.06), ("xp_bonus", 0.5)],
    "artificer": [("knockback_resistance", 0.02), ("thorns", 0.8)],
    "bard": [("xp_bonus", 0.6), ("status_resist", 0.8)],
    "warlock": [("thorns", 0.8), ("status_resist", 0.8)],
}
UNIVERSAL_EXTRA = {
    "adventurer": [("luck", 0.2), ("xp_bonus", 0.5)],
    "wanderer": [("luck", 0.2), ("health_regen", 0.03)],
    "guardian": [("knockback_resistance", 0.02), ("thorns", 0.6)],
}
# Firma de cada subclase: (estadistica del arma, valor), (estadistica de la armadura, valor)
SUBCLASS_EXTRA = {
    "paladin": (("stun_chance", 0.6), ("thorns", 0.8)), "barbarian": (("bleed_chance", 1.0), ("knockback_resistance", 0.02)),
    "death_knight": (("poison_chance", 0.8), ("health_regen", 0.04)), "pyromancer": (("burn_chance", 1.2), ("status_resist", 0.8)),
    "cryomancer": (("freeze_chance", 1.2), ("health_regen", 0.03)), "electromancer": (("shock_chance", 1.2), ("xp_bonus", 0.5)),
    "marksman": (("execute", 2.0), ("luck", 0.2)), "beastmaster": (("bleed_chance", 0.8), ("health_regen", 0.04)),
    "trapper": (("stun_chance", 0.6), ("luck", 0.2)), "assassin": (("execute", 2.0), ("xp_bonus", 0.5)),
    "venomancer": (("poison_chance", 1.2), ("status_resist", 0.8)), "shadow_duelist": (("attack_speed", 0.015), ("thorns", 0.6)),
    "priest_of_light": (("mana_on_hit", 0.6), ("health_regen", 0.06)), "inquisitor": (("burn_chance", 0.8), ("thorns", 0.8)),
    "monk": (("attack_speed", 0.015), ("knockback_resistance", 0.02)), "shadow_lord": (("mana_on_hit", 0.5), ("thorns", 0.6)),
    "reaper": (("execute", 2.0), ("health_regen", 0.03)), "plaguer": (("poison_chance", 1.0), ("status_resist", 0.8)),
    "wild_guardian": (("stun_chance", 0.6), ("knockback_resistance", 0.02)), "apex_hunter": (("bleed_chance", 1.0), ("luck", 0.2)),
    "earth_shaman": (("stun_chance", 0.6), ("health_regen", 0.04)), "alchemist": (("poison_chance", 0.8), ("luck", 0.2)),
    "engineer": (("shock_chance", 0.8), ("thorns", 0.6)), "demolitionist": (("burn_chance", 1.0), ("knockback_resistance", 0.02)),
    "valor_herald": (("attack_speed", 0.01), ("xp_bonus", 0.5)), "discord_master": (("shock_chance", 0.6), ("status_resist", 0.8)),
    "minstrel": (("mana_on_hit", 0.5), ("xp_bonus", 0.5)), "demon_summoner": (("burn_chance", 0.8), ("thorns", 0.8)),
    "cursed": (("freeze_chance", 0.8), ("status_resist", 0.8)), "soul_eater": (("execute", 1.5), ("health_regen", 0.04)),
}
UNIVERSAL_PROFILE = {
    "adventurer": [("max_health", 0.8), ("evasion", 0.5)], "wanderer": [("movement_speed", 0.002), ("evasion", 0.6)],
    "guardian": [("damage_reduction", 0.7), ("armor_toughness", 0.4)],
}
GROUP_NAMES = {"classes": "Clases", "subclasses": "Subclases", "universal": "Universal"}


def set_kind(set_id):
    if set_id in CLASSES:
        return "class"
    if set_id in SUBCLASSES:
        return "subclass"
    return "universal"


def parent_of(set_id):
    return SUBCLASS_IDS[set_id][0] if set_id in SUBCLASS_IDS else set_id


def piece_type(piece):
    if piece == "weapon":
        return "weapon"
    if piece == "offhand_extra":
        return "offhand"
    return "armor"


def build_stats(set_id, piece, tier):
    kind = set_kind(set_id)
    tf = TIER_FACTOR[tier]
    if kind == "universal":
        profile = UNIVERSAL_PROFILE[set_id]
    else:
        parent = parent_of(set_id)
        profile = WEAPON_PROFILE[parent] if piece in ("weapon", "offhand_extra") else ARMOR_PROFILE[parent]
    pf = ARMOR_PIECE_FACTOR.get(piece, 1.0 if piece == "weapon" else 0.5)
    out = {}
    for stat, base in profile:
        out[stat] = round(base * tf * pf, 3)
    is_weapon = piece in ("weapon", "offhand_extra")
    if kind == "universal":
        extra = UNIVERSAL_EXTRA[set_id]
    else:
        extra = (WEAPON_EXTRA if is_weapon else ARMOR_EXTRA)[parent_of(set_id)]
        if kind == "subclass":
            extra = list(extra) + [SUBCLASS_EXTRA[set_id][0 if is_weapon else 1]]
    for stat, base in extra:
        out[stat] = round(out.get(stat, 0) + base * tf * pf, 4)
    return out


def build_requires(set_id, tier):
    kind = set_kind(set_id)
    lvl = TIER_LEVELS[tier]
    if kind == "class":
        return {"class": CLASS_IDS[set_id], "class_level": lvl}
    if kind == "subclass":
        return {"subclass": SUBCLASS_IDS[set_id][1], "subclass_level": lvl}
    return {"class_level": lvl}


def build_recipe_ingredients(set_id_en, piece_name, tier_num, vanilla_mat):
    """Lista "clave:cantidad". MAYUSCULAS = material vanilla, minusculas = id de item .rlvp."""
    units = PIECE_UNITS.get(piece_name, 2)
    if tier_num == 1:
        item_base = get_piece_material(piece_name, vanilla_mat)
        mat_secundario = "LEATHER" if vanilla_mat == "LEATHER" else "IRON_INGOT"
        return [f"{item_base}:1", f"{mat_secundario}:{units + 1}"]
    prev_item_id = f"{set_id_en}_{piece_name}_t{tier_num-1}"
    amount = units if tier_num < 5 else max(1, units // 2)
    return [f"{prev_item_id}:1", f"{TIER_MATERIALS[tier_num]}:{amount}"]

def build_rlvp_item(set_id_en, set_display_es, piece_name, tier_num, base_mat, group="classes"):
    roman_tier = ["I", "II", "III", "IV", "V"][tier_num - 1]
    nombre_pieza = PIECE_TRANSLATIONS.get(piece_name, piece_name)
    ingredients = build_recipe_ingredients(set_id_en, piece_name, tier_num, base_mat)
    
    return {
        "material": get_piece_material(piece_name, base_mat),
        "custom_model_data": next_cmd(),
        "display_name": f"§6§l{nombre_pieza} de {set_display_es} - Nivel {roman_tier}",
        "lore": [
            f"§8Set: {set_display_es}",
            LORE_DESCRIPTIONS[tier_num],
            ""
        ],
        "list": {
            "category": set_id_en,
            "name": set_display_es,
            "group": group,
            "type": piece_type(piece_name),
            "piece": piece_name,
            "tier": tier_num
        },
        "stats": build_stats(set_id_en, piece_name, tier_num),
        "requires": build_requires(set_id_en, tier_num),
        "sockets": SOCKETS_BY_TIER[tier_num] if piece_name != "offhand_extra" else min(1, SOCKETS_BY_TIER[tier_num]),
        "recipe": {
            "station": set_id_en,
            "tier": tier_num,
            "ingredients": ingredients
        },
        "enchantments": [
            {
                "type": "UNBREAKING",
                "level": tier_num
            }
        ],
        "unbreakable": True,
        "custom_data": {
            "rarity": TIER_RARITY[tier_num],
            "set": set_id_en
        }
    }

def generate_class_file(set_id_en, set_display_es, base_arm, weapon_mat, extra_mat, target_dir):
    os.makedirs(target_dir, exist_ok=True)
    file_items = {}
    
    for p in PIECES:
        if p == "weapon":
            base_mat = weapon_mat
        elif p == "offhand_extra":
            base_mat = extra_mat
        else:
            base_mat = base_arm
            
        for t in range(1, 6):
            item_key = f"{set_id_en}_{p}_t{t}"
            file_items[item_key] = build_rlvp_item(set_id_en, set_display_es, p, t, base_mat, os.path.basename(target_dir))

    rlvp_structure = {
        "items": file_items
    }

    file_path = os.path.join(target_dir, f"{set_id_en}.rlvp")
    with open(file_path, "w", encoding="utf-8") as f:
        yaml.dump(rlvp_structure, f, Dumper=UnquotedDumper, sort_keys=False, allow_unicode=True)


# ----------------------------------------------------------------------------------------
# Estaciones de trabajo, materiales y recetas vanilla
# ----------------------------------------------------------------------------------------
# id en ingles del set -> id de clase/subclase del plugin (data/classes, data/subclasses)
CLASS_IDS = {
    "warrior": "guerrero", "mage": "mago", "hunter": "cazador", "rogue": "picaro", "cleric": "clerigo",
    "necromancer": "nigromante", "druid": "druida", "artificer": "artificie", "bard": "bardo", "warlock": "brujo",
}
SUBCLASS_IDS = {
    "paladin": ("warrior", "paladin"), "barbarian": ("warrior", "barbaro"), "death_knight": ("warrior", "caballero_de_la_muerte"),
    "pyromancer": ("mage", "piroamante"), "cryomancer": ("mage", "criomante"), "electromancer": ("mage", "electromante"),
    "marksman": ("hunter", "tirador"), "beastmaster": ("hunter", "maestro_de_bestias"), "trapper": ("hunter", "trampero"),
    "assassin": ("rogue", "asesino"), "venomancer": ("rogue", "envenenador"), "shadow_duelist": ("rogue", "duquista_sombra"),
    "priest_of_light": ("cleric", "sacerdote_de_luz"), "inquisitor": ("cleric", "inquisidor"), "monk": ("cleric", "monje"),
    "shadow_lord": ("necromancer", "senor_de_las_sombras"), "reaper": ("necromancer", "cosechador"), "plaguer": ("necromancer", "plagador"),
    "wild_guardian": ("druid", "guardian_salvaje"), "apex_hunter": ("druid", "cazador_predador"), "earth_shaman": ("druid", "chaman_terrestre"),
    "alchemist": ("artificer", "maestro_alquimista"), "engineer": ("artificer", "ingeniero"), "demolitionist": ("artificer", "demoledor"),
    "valor_herald": ("bard", "ladrador_del_valor"), "discord_master": ("bard", "maestro_de_la_discordia"), "minstrel": ("bard", "menestral"),
    "demon_summoner": ("warlock", "invocador_demoniaco"), "cursed": ("warlock", "maldito"), "soul_eater": ("warlock", "devorador_de_almas"),
}
# Dos materiales vanilla "firma" por clase / subclase para fabricar su estacion
CLASS_SIGNATURE = {
    "warrior": ("IRON_BLOCK", "SHIELD"), "mage": ("LAPIS_BLOCK", "BOOKSHELF"), "hunter": ("BOW", "TARGET"),
    "rogue": ("ENDER_PEARL", "FERMENTED_SPIDER_EYE"), "cleric": ("GOLD_BLOCK", "GLOWSTONE"),
    "necromancer": ("SOUL_SAND", "BONE_BLOCK"), "druid": ("MOSS_BLOCK", "SWEET_BERRIES"),
    "artificer": ("REDSTONE_BLOCK", "PISTON"), "bard": ("NOTE_BLOCK", "GOAT_HORN"), "warlock": ("OBSIDIAN", "ENDER_EYE"),
}
SUBCLASS_SIGNATURE = {
    "paladin": ("GOLDEN_APPLE", "GOLD_BLOCK"), "barbarian": ("IRON_AXE", "COOKED_BEEF"), "death_knight": ("WITHER_ROSE", "NETHERITE_SCRAP"),
    "pyromancer": ("BLAZE_ROD", "MAGMA_BLOCK"), "cryomancer": ("BLUE_ICE", "SNOW_BLOCK"), "electromancer": ("LIGHTNING_ROD", "COPPER_BLOCK"),
    "marksman": ("SPYGLASS", "CROSSBOW"), "beastmaster": ("SADDLE", "LEAD"), "trapper": ("TRIPWIRE_HOOK", "COBWEB"),
    "assassin": ("IRON_SWORD", "BLACK_DYE"), "venomancer": ("POISONOUS_POTATO", "SPIDER_EYE"), "shadow_duelist": ("ECHO_SHARD", "SCULK"),
    "priest_of_light": ("GLOWSTONE", "TOTEM_OF_UNDYING"), "inquisitor": ("SOUL_LANTERN", "BELL"), "monk": ("BAMBOO_BLOCK", "HONEY_BOTTLE"),
    "shadow_lord": ("SKELETON_SKULL", "COAL_BLOCK"), "reaper": ("IRON_HOE", "WHEAT"), "plaguer": ("ROTTEN_FLESH", "FERMENTED_SPIDER_EYE"),
    "wild_guardian": ("MOSSY_COBBLESTONE", "SWEET_BERRIES"), "apex_hunter": ("BONE", "RABBIT_HIDE"), "earth_shaman": ("DRIPSTONE_BLOCK", "CLAY"),
    "alchemist": ("BREWING_STAND", "GLASS_BOTTLE"), "engineer": ("REPEATER", "HOPPER"), "demolitionist": ("TNT", "GUNPOWDER"),
    "valor_herald": ("WHITE_BANNER", "GOLDEN_CARROT"), "discord_master": ("DISC_FRAGMENT_5", "FIRE_CHARGE"), "minstrel": ("BELL", "STRING"),
    "demon_summoner": ("NETHER_WART_BLOCK", "CRYING_OBSIDIAN"), "cursed": ("SOUL_TORCH", "OBSIDIAN"), "soul_eater": ("GHAST_TEAR", "SOUL_SOIL"),
}
UNIVERSAL_SIGNATURE = {
    "adventurer": ("IRON_INGOT", "COMPASS"), "wanderer": ("LEATHER", "MAP"), "guardian": ("IRON_BLOCK", "SHIELD"),
}
STATION_CMD_BASE = 913000
MATERIAL_CMD_BASE = 912000

MATERIALS = [
    # id, material, nombre, lore
    ("steel_ingot", "IRON_INGOT", "§7§lLingote de Acero", "§7Hierro templado. Se mejora a Nivel II."),
    ("cobalt_ingot", "COPPER_INGOT", "§9§lLingote de Cobalto", "§7Aleación azul. Se mejora a Nivel III."),
    ("sky_ingot", "NETHERITE_INGOT", "§b§lLingote Celeste", "§7Forjado con luz de los cielos. Nivel IV."),
    ("dragon_core", "ECHO_SHARD", "§5§lNúcleo de Dragón", "§7Poder del Dragón. Se mejora a Nivel V."),
]


def write_yaml(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        yaml.dump(data, f, Dumper=UnquotedDumper, sort_keys=False, allow_unicode=True)


def generate_stations_and_recipes():
    names = {}
    for cid, info in CLASSES.items():
        names[cid] = ("class", info["display"])
    for sid, disp in SUBCLASSES.items():
        names[sid] = ("subclass", disp)
    for uid, disp in UNIVERSAL_SETS.items():
        names[uid] = ("universal", disp)

    # --- materiales (.rlvp)
    mats = {}
    for i, (mid, mat, name, lore) in enumerate(MATERIALS, start=1):
        mats[mid] = {
            "material": mat,
            "custom_model_data": MATERIAL_CMD_BASE + i,
            "display_name": name,
            "lore": [lore, "§8Material de forja"],
            "list": {"category": "materials", "name": "Materiales de forja", "group": "materials", "type": "material", "piece": mid, "tier": i + 1},
            "custom_data": {"material": mid},
        }
    write_yaml("items/materials/materials.rlvp", {"items": mats})

    # --- estaciones: items (.rlvp), definicion (yml) y receta vanilla para obtenerlas
    block = {"class": "SMITHING_TABLE", "subclass": "CARTOGRAPHY_TABLE", "universal": "FLETCHING_TABLE"}
    station_items, station_defs, recipes = {}, {}, {}
    for n, (sid, (kind, disp)) in enumerate(names.items(), start=1):
        item_id = f"station_{sid}"
        station_items[item_id] = {
            "material": block[kind],
            "custom_model_data": STATION_CMD_BASE + n,
            "display_name": f"§6§lEstación de {disp}",
            "lore": [
                f"§7Estación de trabajo de {'la clase' if kind == 'class' else 'la subclase' if kind == 'subclass' else 'equipo'} §f{disp}§7.",
                "§7Colócala y usa §eclic derecho §7para forjar",
                f"§7el equipo del set §f{disp}§7.",
            ],
            "list": {"category": "stations", "name": "Estaciones de trabajo", "group": "stations", "type": "station", "piece": kind, "tier": 0},
            "custom_data": {"station": sid},
        }
        entry = {"name": disp, "kind": kind, "item": item_id, "block": block[kind]}
        if kind == "class":
            entry["owner"] = CLASS_IDS[sid]
            a, b = CLASS_SIGNATURE[sid]
            center = "SMITHING_TABLE"
        elif kind == "subclass":
            parent, sub_id = SUBCLASS_IDS[sid]
            entry["owner"] = sub_id
            entry["parent"] = CLASS_IDS[parent]
            a, b = SUBCLASS_SIGNATURE[sid]
            center = f"station_{parent}"      # requiere la estacion de la clase padre
        else:
            a, b = UNIVERSAL_SIGNATURE[sid]
            center = "CRAFTING_TABLE"
        station_defs[sid] = entry
        recipes[item_id] = {
            "type": "shaped",
            "result": item_id,
            "shape": ["ABA", "BSB", "ABA"],
            "ingredients": {"A": a, "B": b, "S": center},
        }

    write_yaml("items/stations/stations.rlvp", {"items": station_items})
    write_yaml("recipes/stations.yml", {"stations": station_defs})

    # --- materiales: recetas en estaciones vanilla (mesa de crafteo / alto horno)
    recipes["steel_ingot"] = {"type": "shapeless", "result": "steel_ingot", "ingredients": ["IRON_INGOT:3", "COAL:1"]}
    recipes["steel_ingot_blast"] = {"type": "blasting", "result": "steel_ingot", "amount": 2, "input": "IRON_BLOCK", "time": 400, "xp": 1.0}
    recipes["cobalt_ingot"] = {"type": "shapeless", "result": "cobalt_ingot", "ingredients": ["steel_ingot:2", "LAPIS_LAZULI:2", "COPPER_INGOT:1"]}
    recipes["sky_ingot"] = {"type": "shapeless", "result": "sky_ingot", "ingredients": ["cobalt_ingot:2", "DIAMOND:1", "PHANTOM_MEMBRANE:1"]}
    recipes["dragon_core"] = {"type": "shapeless", "result": "dragon_core", "ingredients": ["sky_ingot:3", "DRAGON_BREATH:1", "ECHO_SHARD:1"]}
    write_yaml("recipes/recipes.yml", {"recipes": recipes})

    # --- bonos de set (gear_sets/sets.yml): 2 / 4 / 6 piezas equipadas del mismo set
    sets = {}
    for sid, (kind, disp) in names.items():
        if kind == "universal":
            arm, wep = UNIVERSAL_PROFILE[sid], UNIVERSAL_PROFILE[sid]
        else:
            parent = parent_of(sid)
            arm, wep = ARMOR_PROFILE[parent], WEAPON_PROFILE[parent]
        def val(entry, mult):
            return round(entry[1] * mult, 3)
        b2 = {arm[0][0]: val(arm[0], 3)}
        b4 = {wep[0][0]: val(wep[0], 3)}
        b6 = {wep[1][0]: val(wep[1], 4), arm[1][0]: val(arm[1], 4)}
        sets[sid] = {"name": disp, "bonuses": {2: {"stats": b2}, 4: {"stats": b4}, 6: {"stats": b6}}}
    write_yaml("gear_sets/sets.yml", {"sets": sets})
    print(f"Estaciones: {len(station_defs)}, materiales: {len(mats)}, recetas vanilla: {len(recipes)}")


if __name__ == "__main__":
    base_dir = "items"
    base_classes_dir = os.path.join(base_dir, "classes")
    subclasses_dir = os.path.join(base_dir, "subclasses")
    universal_dir = os.path.join(base_dir, "universal")

    for c_id, c_info in CLASSES.items():
        generate_class_file(c_id, c_info["display"], c_info["base_arm"], c_info["weapon"], c_info["extra"], base_classes_dir)

    for sub_id, sub_display in SUBCLASSES.items():
        base_arm = "GOLDEN" if "paladin" in sub_id or "inquisitor" in sub_id else "LEATHER"
        generate_class_file(sub_id, sub_display, base_arm, "DIAMOND_SWORD", "SHIELD", subclasses_dir)

    for u_id, u_display in UNIVERSAL_SETS.items():
        generate_class_file(u_id, u_display, "IRON", "IRON_SWORD", "SHIELD", universal_dir)

    generate_stations_and_recipes()
    print("¡Archivos generados con el formato exacto sin comillas redundantes!")