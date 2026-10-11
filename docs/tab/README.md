# TAB (v6.2.0): tablist compacto + scoreboard de AnacroniCraft

Basado en la wiki oficial de TAB (header-footer, layout, scoreboard, animaciones, ordenado). Necesita PlaceholderAPI y el jar actualizado de RPGLeVeL.

## Instalar (3 archivos, sin editar a mano)
1. Haz copia de `plugins/TAB/`.
2. Sustituye `config.yml`, `animations.yml` y `groups.yml` de `plugins/TAB/` por los de esta carpeta (`config.yml` es la config oficial de TAB 6.2.0 con nuestras secciones; si tenías otros ajustes propios en tu config, compáralos con la copia).
3. Sustituye el jar de RPGLeVeL, reinicia y comprueba: `/papi parse me %rlv_build%` debe devolver `tab3`. Si devuelve el texto sin resolver, el servidor sigue con el jar viejo.
4. El staff aparece primero en la lista según el orden de grupos de `scoreboard-teams.sorting-types` (owner, admin, mod, helper...). Ajusta esa lista a tus grupos reales.
5. `/tab reload`. Guarda los archivos en UTF-8.

## Qué muestra
- **Tablist:** el normal de Minecraft (crece solo con los jugadores, hasta 4 columnas), con `[Rango] [Clase] Nombre Nv.X` y el ping al lado. La información va en la cabecera (título animado, bienvenida, en línea/staff, TPS, ping) y el pie (hora y Discord). El perfil completo está en el scoreboard. En `config.yml` queda una plantilla `layout` de 2 columnas, desactivada.
- **Scoreboard (`/sb` lo oculta/muestra):** `moderno` (1.20.3+, valores a la derecha sin números rojos) y `clasico` (1.20.2- y Bedrock): jugador y clase, barra de XP, barra de maná, monedas, rango, servidor, temporada y evento (la línea desaparece si no hay).
- Layout (opcional): `layout.enabled: true` y `playerlist-objective.enabled: false` (son incompatibles).

## Placeholders de RPGLeVeL usados
`%rlv_class_text%`, `%rlv_subclass_text%`, `%rlv_level_suffix%`, `%rlv_class_xp_bar%`, `%rlv_class_xp_pct%`, `%rlv_mana_bar%`, `%rlv_mana%`, `%rlv_rank_class%`, `%rlv_rank_class_season%`, `%rlv_coins%`, `%rlv_season%`, `%rlv_season_left%`, `%rlv_event_text%`, `%rlv_build%` (diagnóstico). Pruébalos con `/papi parse me <placeholder>`.

## Notas
- Si un placeholder se ve crudo (`%rlv_...%`), PlaceholderAPI no lo reconoce: jar viejo o expansión no registrada (mira en consola "PlaceholderAPI expansion registered"; `/papi list` debe incluir `rlv`).
- El scoreboard usa 15 líneas (máximo del cliente). Animaciones en `animations.yml` (generado con `tools/make_tab.py`; incluye `time`, `web` y `Welcome` del config por defecto de TAB).
- Layout: no uses `|` dentro del texto de un slot (es el separador SLOT|TEXTO). Las cabezas de los slots fijos usan la skin por defecto; para ocultarlas la wiki de TAB documenta un truco de resource pack (FAQ nº 6).
- `config-secciones.yml` queda como referencia de las secciones que cambian.
