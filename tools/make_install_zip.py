#!/usr/bin/env python3
"""Genera dist/RPGLeVeL-instalacion.zip y dist/rlvp-items.zip (ejecutar tras `sh gradlew clean build`)."""
import os, shutil, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
DIST = os.path.join(ROOT, "dist")
JAR = os.path.join(ROOT, "build/libs/RPGLeVeL-1.1.0.jar")

LEEME = """RPGLeVeL - instalación
1. Detén el servidor.
2. Copia el .jar y la carpeta RPGLeVeL de este zip dentro de la carpeta plugins de tu servidor (mezclando con la existente).
3. Si ya tenías plugins/RPGLeVeL/data/ de una versión anterior, borra (o renombra) al menos data/items, data/recipes,
   data/gear_sets, data/stats y data/jobs: los archivos existentes NUNCA se sobrescriben y los nuevos traen formatos nuevos.
   Tu config.yml tampoco se sobrescribe: compara con el del zip (secciones nuevas: stations, requirements, mana, combat, party,
   prestige, economy, mobs, bosses, events, guild, auction, storage, anti_abuse, chat...).
4. Inicia el servidor. Opcionales: Vault (economía real) y PlaceholderAPI (%rlv_...%).

Estructura (cada sección en su carpeta, todo editable; /rlv editor edita los archivos desde el juego):
  data/items/        ítems .rlvp: classes/, subclasses/, universal/, materials/, stations/ (la extensión .rlvp es obligatoria)
  data/recipes/      stations.yml (estaciones de clase/subclase) y recipes.yml (recetas en estaciones vanilla)
  data/classes/ subclasses/ jobs/ (con ventajas por nivel) quests/ (diarias, weekly.yml, chains.yml)
  data/abilities/    habilidades: settings.yml, classes/, subclasses/, items.yml
  data/stats/        estadísticas por nivel de clase y subclase
  data/gear_sets/    bonos por 2/4/6 piezas del mismo set
  data/affixes/ gems/ talents/ achievements/ loot/ bosses/ dungeons/

Lectura rápida:
  RPGLeVeL/docs/COMANDOS_DISCORD.md  guía de comandos para pegar en Discord (usuario, clases, subclases, trabajos, misiones, rangos, recetas)
  /rlv help                 todos los comandos de staff
  /rlv userinfo <jugador>   toda la información de un jugador (también: /class @jugador, /ability @jugador, ...)

Los archivos de data/ ya vienen dentro del plugin: si no los copias, se extraen solos al iniciar.
Los archivos antiguos (data/classes.yml, subclasses.yml, jobs.yml, quests.yml, rpg_abilities.yml) se renombran a .old.
Zonas, gremios y subastas se guardan en zones.yml, guilds.yml y auctions.yml; los jugadores en players.yml (o players.db / MySQL).
"""

os.makedirs(DIST, exist_ok=True)
shutil.copy(JAR, os.path.join(DIST, "RPGLeVeL-1.1.0.jar"))

data_root = os.path.join(RES, "data")
with zipfile.ZipFile(os.path.join(DIST, "RPGLeVeL-instalacion.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    z.write(JAR, "RPGLeVeL-1.1.0.jar")
    z.write(os.path.join(RES, "config.yml"), "RPGLeVeL/config.yml")
    for d, _, files in os.walk(data_root):
        for f in sorted(files):
            if f.endswith((".yml", ".rlvp")):
                full = os.path.join(d, f)
                z.write(full, "RPGLeVeL/data/" + os.path.relpath(full, data_root).replace(os.sep, "/"))
    z.writestr("RPGLeVeL/LEEME.txt", LEEME)
    z.write(os.path.join(ROOT, "docs/COMANDOS_DISCORD.md"), "RPGLeVeL/docs/COMANDOS_DISCORD.md")
    z.write(os.path.join(ROOT, "docs/UTILIDADES.md"), "RPGLeVeL/docs/UTILIDADES.md")
    # carpeta de TAB (el zip se extrae directamente dentro de plugins/). Kits y LuckPerms NO van en el zip: viven en docs/ del repo
    docs = os.path.join(ROOT, "docs")
    for f in ("README.md", "config.yml", "animations.yml", "groups.yml", "config-secciones.yml"):
        z.write(os.path.join(docs, "tab", f), "TAB/" + f)

with zipfile.ZipFile(os.path.join(DIST, "rlvp-items.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    for d, _, files in os.walk(os.path.join(data_root, "items")):
        for f in sorted(files):
            if f.endswith(".rlvp"):
                full = os.path.join(d, f)
                z.write(full, os.path.relpath(full, os.path.join(data_root, "items")).replace(os.sep, "/"))
print("OK")
