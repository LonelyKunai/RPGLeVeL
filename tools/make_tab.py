#!/usr/bin/env python3
"""Genera docs/tab/animations.yml (animaciones con degradado/brillo por frame). Config estatica en docs/tab/*.yml."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "tab", "animations.yml")
TXT = "AnacroniCraft"
A, B = (0x54, 0xDA, 0xF4), (0x54, 0x5E, 0xB6)  # cian -> azul (mismo degradado que el prefijo Owner)


def mix(c1, c2, t):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def frame(pos, text=TXT, width=2.5):
    out = []
    n = len(text)
    for i, ch in enumerate(text):
        base = mix(A, B, i / (n - 1))
        w = max(0.0, 1 - abs(i - pos) / width) * 0.85
        r, g, b = mix(base, (255, 255, 255), w)
        out.append(f"&#{r:02X}{g:02X}{b:02X}&l{ch}")
    return "".join(out)


def shimmer(text, pause):
    n = len(text)
    fr = [frame(p, text) for p in [x * 0.5 - 2 for x in range(int((n + 4) * 2))]]
    return fr + [frame(-99, text)] * pause


def block(name, interval, texts):
    s = [f"{name}:", f"  change-interval: {interval}", "  texts:"]
    s += [f'    - "{t}"' for t in texts]
    return "\n".join(s)


L = ["# animations.yml de TAB (v6.2.0): sustituye el archivo (incluye time/web/Welcome del config.yml por defecto de TAB).",
     "# Uso: %animation:<nombre>%  (change-interval en milisegundos). Generado por tools/make_tab.py.", ""]
L.append(block("Titulo", 70, shimmer(TXT, 20)))
L.append("")
L.append(block("ScoreTitulo", 90, shimmer(TXT, 24)))
L.append("")
L.append(block("Evento", 400, ["&c&l¡EVENTO!", "&6&l¡EVENTO!", "&e&l¡EVENTO!", "&6&l¡EVENTO!"]))
L.append("")
L.append(block("time", 1000, ["&8%date% &7» &f%time%"]))
L.append("")
web = "discord.gg/AuvDQU5GaK"
L.append(block("web", 120, [f"&9&lDiscord &8» {frame(p, web, 3.5).replace('&l', '')}" for p in [x * 1.0 - 3 for x in range(len(web) + 6)]] + [f"&9&lDiscord &8» &b{web}"] * 12))
L.append("")
L.append(block("Welcome", 700, ["&7&lBienvenid@", "&f&lBienvenid@"]))
open(OUT, "w", encoding="utf-8").write("\n".join(L) + "\n")
print("OK", len(L))


# ---- config.yml completo: config oficial de TAB 6.2.0 con nuestras secciones sustituidas ----
import re

SEC = os.path.join(ROOT, "docs", "tab", "config-secciones.yml")
BASE = os.path.join(ROOT, "tools", "tab_config_oficial_6.2.0.yml")


def split_top(lines):
    """-> lista de (clave, texto) por clave de nivel 0; los comentarios previos a una clave pertenecen a ella."""
    keys = [i for i, l in enumerate(lines) if re.match(r"^[A-Za-z][\w-]*:", l)]
    starts = []
    for i in keys:
        j = i
        while j > 0 and (lines[j - 1].startswith("#") or lines[j - 1].strip() == ""):
            j -= 1
        starts.append(j if not starts or j > starts[-1] else i)
    out = [("__head__", "".join(lines[:starts[0]]))] if starts[0] else []
    for n, i in enumerate(keys):
        end = starts[n + 1] if n + 1 < len(keys) else len(lines)
        out.append((re.match(r"^([\w-]+):", lines[i]).group(1), "".join(lines[starts[n]:end])))
    return out


base = split_top(open(BASE, encoding="utf-8").read().splitlines(True))
new = {k: t for k, t in split_top(open(SEC, encoding="utf-8").read().splitlines(True)) if k != "__head__"}
res, used = [], set()
for k, t in base:
    if k in new:
        res.append(new[k].rstrip("\n") + "\n\n")
        used.add(k)
    elif k == "placeholders":
        t = (t.replace('date-format: "dd.MM.yyyy"', 'date-format: "dd/MM/yyyy"')
              .replace('time-format: "[HH:mm:ss / h:mm a]"', 'time-format: "HH:mm"')
              .replace("locale: en-US", "locale: es-ES"))
        res.append(t)
    else:
        res.append(t)
assert used == set(new), (used, set(new))
cfg = "# config.yml de TAB 6.2.0 para AnacroniCraft: base oficial + header-footer, tablist compacto (layout) y scoreboard propios.\n# Generado por tools/make_tab.py. Haz copia de tu config.yml antes de reemplazarlo. Guardar en UTF-8.\n" + "".join(res)
open(os.path.join(ROOT, "docs", "tab", "config.yml"), "w", encoding="utf-8").write(cfg)
print("config.yml OK", len(cfg.splitlines()), "líneas")
