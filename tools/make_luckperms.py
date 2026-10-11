#!/usr/bin/env python3
"""Genera docs/luckperms/ranks.txt (comandos de LuckPerms, uno por linea) y docs/LUCKPERMS.md."""
import os, json, gzip

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "luckperms")
os.makedirs(OUT, exist_ok=True)

# (id de clase del plugin, nombre, color)
CLASSES = [
    ("artificie", "Artífice", "&6"), ("bardo", "Bardo", "&d"), ("brujo", "Brujo", "&5"),
    ("cazador", "Cazador", "&a"), ("clerigo", "Clérigo", "&f"), ("druida", "Druida", "&2"),
    ("guerrero", "Guerrero", "&c"), ("mago", "Mago", "&b"), ("nigromante", "Nigromante", "&8"),
    ("picaro", "Pícaro", "&e"),
]
# rangos de pago universales, de menor a mayor: (id, nombre, color, [permisos propios del escalon])
PAID = [
    ("bronce", "Bronce", "&6", ["essentials.chat.color", "essentials.nick"]),
    ("plata", "Plata", "&7", ["essentials.hat"]),
    ("oro", "Oro", "&e", ["essentials.workbench"]),
    ("platino", "Platino", "&f", ["essentials.enderchest"]),
    ("esmeralda", "Esmeralda", "&a", ["essentials.feed"]),
    ("rubi", "Rubí", "&c", ["essentials.repair"]),
    ("zafiro", "Zafiro", "&9", ["essentials.heal"]),
    ("amatista", "Amatista", "&5", ["essentials.anvil"]),
    ("diamante", "Diamante", "&b&l", []),
    ("mitico", "Mítico", "&d&l", []),
]

CLASS_WEIGHT = 100
CLASS_PREFIX_PRIORITY = 50
PAID_WEIGHT = 200          # + 10 por escalon
PAID_PREFIX_PRIORITY = 100  # + escalon

L = ["# RPGLeVeL - rangos de LuckPerms. Pega estas lineas en la consola del servidor (una por linea).",
     "# 10 grupos de clase (gratuitos) + 10 rangos de pago universales. Ver docs/LUCKPERMS.md.",
     "",
     "# --- tracks ---",
     "lp createtrack clase",
     "lp createtrack pago",
     "lp createtrack staff",
     "lp track staff append owner",
     "lp group owner permission set tab.staff true",
     "",
     "# --- rangos de clase (gratuitos) ---"]
for cid, name, col in CLASSES:
    g = f"class_{cid}"
    L += [f"lp creategroup {g}",
          f"lp group {g} setweight {CLASS_WEIGHT}",
          f"lp group {g} setdisplayname \"{name}\"",
          f"lp group {g} meta addprefix {CLASS_PREFIX_PRIORITY} \"{col}[{name}]&r\"",
          f"lp group {g} permission set playerkits.kit.clase_{cid} true",
          f"lp track clase append {g}",
          ""]
L.append("# --- rangos de pago universales (cada uno hereda el anterior; un jugador solo tiene el mas alto) ---")
prev = None
for i, (pid, name, col, perms) in enumerate(PAID):
    g = f"vip_{pid}"
    L += [f"lp creategroup {g}",
          f"lp group {g} setweight {PAID_WEIGHT + 10 * i}",
          f"lp group {g} setdisplayname \"{name}\"",
          f"lp group {g} meta addprefix {PAID_PREFIX_PRIORITY + i} \"{col}[{name}]&r\""]
    if prev:
        L.append(f"lp group {g} parent add {prev}")
    for p in perms:
        L.append(f"lp group {g} permission set {p} true")
    L += [f"lp group {g} permission set playerkits.kit.vip_{pid} true",
          f"lp track pago append {g}",
          ""]
    prev = g
L += ["# --- fin. Comprobar: /lp listgroups  y  /lp group vip_oro info"]
open(os.path.join(OUT, "ranks.txt"), "w", encoding="utf-8").write("\n".join(L) + "\n")


# --- importacion de un solo paso: /lp import rlv-ranks (archivo en plugins/LuckPerms/) ---
def node(key, value=True):
    return {"type": "permission", "key": key, "value": value}

groups, tracks = {}, {"clase": {"groups": []}, "pago": {"groups": []}, "staff": {"groups": ["owner"]}}
for cid, name, col in CLASSES:
    g = f"class_{cid}"
    groups[g] = {"nodes": [node(f"weight.{CLASS_WEIGHT}"), node(f"displayname.{name}"),
                           node(f"prefix.{CLASS_PREFIX_PRIORITY}.{col}[{name}]&r"),
                           node(f"playerkits.kit.clase_{cid}")]}
    tracks["clase"]["groups"].append(g)
prev = None
for i, (pid, name, col, perms) in enumerate(PAID):
    g = f"vip_{pid}"
    nodes = [node(f"weight.{PAID_WEIGHT + 10 * i}"), node(f"displayname.{name}"),
             node(f"prefix.{PAID_PREFIX_PRIORITY + i}.{col}[{name}]&r")]
    if prev:
        nodes.append(node(f"group.{prev}"))
    nodes += [node(p) for p in perms] + [node(f"playerkits.kit.vip_{pid}")]
    groups[g] = {"nodes": nodes}
    tracks["pago"]["groups"].append(g)
    prev = g
groups["owner"] = {"nodes": [node("tab.staff")]}  # se mezcla con el grupo owner existente (import sin --replace)
with gzip.open(os.path.join(OUT, "rlv-ranks.json.gz"), "wt", encoding="utf-8") as f:
    json.dump({"groups": groups, "tracks": tracks}, f, ensure_ascii=False)

md = f"""# Rangos de LuckPerms (RPGLeVeL)

20 grupos: **10 de clase (gratuitos)** + **10 universales de pago**. Los de pago no tocan los de clase.

## 1. Crear los grupos
**Rápido (recomendado):** copia `rlv-ranks.json.gz` a `plugins/LuckPerms/` y ejecuta en consola `lp import rlv-ranks`. Crea los 20 grupos y los 2 tracks de golpe (mezcla, no borra nada existente).
**Manual:** pega `ranks.txt` en la consola del servidor (son ~110 líneas; en consola de Pterodactyl/screen se pegan de una vez) o ejecútalas por tandas.

## 2. Mostrar los DOS prefijos (editar `plugins/LuckPerms/config.yml`)
Por defecto LuckPerms muestra solo el prefijo de mayor prioridad: el de pago taparía el de clase. Usa el apilado oficial (`meta-formatting`) con los tracks `pago` y `clase`:

```yaml
meta-formatting:
  prefix:
    format:
      - "highest_on_track_staff"
      - "highest_on_track_pago"
      - "highest_on_track_clase"
      - "highest"
    duplicates: first-only
    start-spacer: ""
    middle-spacer: " "
    end-spacer: " "
  suffix:
    format:
      - "highest"
    duplicates: first-only
    start-spacer: ""
    middle-spacer: " "
    end-spacer: ""
```
Reinicia el servidor. Resultado: sin rango de pago `&c[Guerrero]`; con pago `&e[Oro] &c[Guerrero]`; owner `Owner &e[Oro] &c[Guerrero]`; sin clase ni pago, el prefijo de `default` (`Usuario`). Se respetan tus grupos existentes `owner` (peso 1000, track `staff`) y `default` (peso 1); este último solo se muestra si el jugador no tiene rango de pago ni de clase (el `highest` final repite uno ya mostrado y se descarta). Vault (EssentialsChat), `%luckperms_prefix%` (TAB) y DiscordSRV usan este formato.

## 3. Asignar
- Clase: `lp user <jugador> parent add class_<claseid>` (ids: {', '.join(c[0] for c in CLASSES)}). Al cambiar de clase quita el anterior: `lp user <jugador> parent remove class_<viejo>`.
- Pago: `lp user <jugador> parent add vip_<id>` (ids: {', '.join(p[0] for p in PAID)}). Al subir de escalón quita el anterior: `lp user <jugador> parent remove vip_<viejo>` (o usa el track: `lp user <jugador> promote pago`).

## 4. Tabla
| Grupo | Peso | Prefijo (prioridad) | Permisos propios |
|---|---|---|---|
""" + "\n".join(f"| class_{c[0]} | {CLASS_WEIGHT} | {c[2]}[{c[1]}] ({CLASS_PREFIX_PRIORITY}) | playerkits.kit.clase_{c[0]} |" for c in CLASSES) + "\n" + "\n".join(
    f"| vip_{p[0]} | {PAID_WEIGHT + 10 * i} | {p[2]}[{p[1]}] ({PAID_PREFIX_PRIORITY + i}) | {', '.join(p[3] + ['playerkits.kit.vip_' + p[0]])} |" for i, p in enumerate(PAID)) + """

Los de pago heredan al anterior: Mítico tiene todo lo de Bronce…Diamante. Son comodidades y cosméticos, no ventajas de combate.
Los nodos de Essentials (`essentials.*`) y `playerkits.kit.<kit>` son los habituales de esos plugins; compruébalos en tu servidor con `/lp verbose on` o `/lp user <jugador> permission check <nodo>`.
"""
open(os.path.join(ROOT, "docs", "LUCKPERMS.md"), "w", encoding="utf-8").write(md)
print("OK", len(L), "lineas")
