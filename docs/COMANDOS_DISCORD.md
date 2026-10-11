# 📖 Guía de comandos — RPGLeVeL

> Cada bloque separado por `────` cabe en **un mensaje de Discord** (< 2000 caracteres). Copia y pega por bloques.
> Los comandos entre `< >` son obligatorios y entre `[ ]` opcionales. Casi todos tienen autocompletado con **Tab**.

────────────────────────────────────────

## 👤 Comandos de usuario
```
/class [gui|list|info <id>|stats|abilities|rank|<id>]   Elegir y ver tu clase
/subclass [gui|list|info <id>|stats|abilities|rank|<id>] Elegir y ver tu subclase
/jobs [gui|list|me|info <id>|set <1|2> <id>|clear <1|2>|rank]
/quest [gui|list|info <id>|weekly|chain|track|rank]
/rank [class|subclass|job|quest|pvp] [perm|season]
/ability [gui|list|info|unlock|upgrade|copy <id>|points|bind|unbind|hotbar|respec]
/station [list|own|<id>]      Ver recetas de una estación
/talent   /prestige   /title   /achievements   /stats
/coins  /pay <jugador> <cantidad>   (si Essentials ya usa /pay: /rpgleveltier:pay)
/party  /pc <msg>   /guild  /gc <msg>
/duel <jugador> [apuesta]   /dungeon [list|start <id>|leave]
/trade <jugador>   /ah [sell <precio>|mine|claim]
/shop [minerales|materias_primas|raros|sell [n|all]|price <objeto>|info]
/language <es|en>
```
**Intercambio:** `/trade <jugador>` → ofrece **ítems arriba y dinero abajo a la vez** (− / + y clic en el monto cambia el paso); ambos confirman y se completa en 3 segundos.
**Atajos:** `/rank me`, `/rank top <cat> 10 season`, `/quest track` (marcador lateral).
**Habilidades en combate:** bloquea hasta 4 con `/ability bind <1-4> <id>` → **F** lanza la ranura elegida, **Shift+F** cambia de ranura. También puedes copiar una habilidad a un tomo con `/ability copy <id>` y usarlo con clic derecho.

────────────────────────────────────────
## ⚔️ Clases
Elige una con `/class` (menú) o `/class <id>`. Cada clase sube de nivel (1-50) al matar monstruos y completar misiones; cada nivel mejora tus estadísticas, da **ability XP** y cada 3 niveles un **punto de habilidad** para el árbol (`/ability`).
```
guerrero    Guerrero    Cuerpo a cuerpo, resistencia, primera línea
mago        Mago        Magia arcana y elemental, daño en área
cazador     Cazador     Distancia, movilidad, supervivencia
picaro      Pícaro      Sigilo, ráfagas y evasión
clerigo     Clérigo     Curación, bendiciones y purificación
nigromante  Nigromante  Muertos vivientes y energía vital
druida      Druida      Naturaleza y metamorfosis
artificie   Artífice    Tecnología, redstone y alquimia
bardo       Bardo       Canciones y auras que apoyan/debilitan
brujo       Brujo       Magia caótica y pactos demoníacos
```
Elegir tu primera clase es gratis; cambiarla después cuesta monedas y tiene enfriamiento (300 monedas / 12 h por defecto).
Stats por nivel: vida, daño, armadura y los propios de cada clase (crítico, maná, robo de vida, evasión, reducción de enfriamiento...). Mira los tuyos con `/stats` o `/class stats`.

────────────────────────────────────────
## 🌟 Subclases
Se eligen con `/subclass` y dependen de tu clase (3 por clase).
```
Guerrero    → Paladín, Bárbaro, Caballero de la Muerte
Mago        → Piroamante, Criomante, Electromante
Cazador     → Tirador, Maestro de Bestias, Trampero
Pícaro      → Asesino, Envenenador, Duquista / Sombra
Clérigo     → Sacerdote de Luz, Inquisidor, Monje
Nigromante  → Señor de las Sombras, Cosechador, Plagador
Druida      → Guardián Salvaje, Cazador Depredador, Chamán Terrestre
Artífice    → Maestro Alquimista, Ingeniero, Demoledor
Bardo       → Ladrador del Valor, Maestro de la Discordia, Menestral
Brujo       → Invocador Demoníaco, Maldito, Devorador de Almas
```
Necesitas **nivel de clase 10** para elegir subclase (cambiarla después cuesta 150 monedas).
Tienen su propio nivel, stats y árbol de habilidades (`/ability gui subclass`).

────────────────────────────────────────
## 🛠️ Trabajos
Puedes tener **2 trabajos globales** (`/jobs set 1 <id>` / `set 2 <id>`). Ganas XP del trabajo con la actividad y desbloqueas **ventajas** en los niveles 5, 10, 20, 30 y 50 (`/jobs info <id>`).
```
miner       Minero       minerales
lumberjack  Leñador      talar troncos
farmer      Granjero     cosechar cultivos maduros
fisher      Pescador     pescar
hunter      Cazador      matar mobs
blacksmith  Herrero      forjar en estaciones: prob. de afijos extra
                         (nivel x0.5%, máx 40%) y de ahorrar materiales
alchemist   Alquimista   sacar pociones del soporte de pociones
enchanter   Encantador   encantar objetos
cook        Cocinero     sacar comida de hornos/ahumadores
```
Los bloques que tú pones no dan XP al romperlos (anti-farmeo).

────────────────────────────────────────
## ✨ Habilidades de oficio
`/jobs abilities [trabajo]` — cada trabajo tiene sus habilidades pasivas. Solo funcionan con el trabajo **equipado** y suben de nivel solas con el nivel del trabajo.
Se desbloquean de 3 formas:
• **Por nivel del trabajo** (p. ej. minero nv5 → doble drop de minerales).
• **Por rango de trabajo** (rango D o superior → pasivas de estadísticas).
• **Al azar**, como recompensa de misiones (diarias, semanales y cadenas), mazmorras y jefes: Veta continua (picar toda la veta), Talador (derribar el árbol), Cosecha en área, Cazatesoros, Cazarrecompensas, Toque del artesano, Mezcla volátil, Visión arcana y Banquete.
Truco: agáchate (**Shift**) al picar, talar o cosechar para activar las habilidades de área.

────────────────────────────────────────
## 🛒 Tienda (`/shop`)
Menú con 3 categorías: **Minerales** (stock alto), **Materias primas renovables** (stock medio) y **Objetos raros** (stock bajo: perlas, restos de netherita, materiales de forja y gemas).
• **Stock según jugadores:** a más jugadores activos, más stock. Se **rellena cada semana** (lunes 04:00).
• **Precio dinámico:** con mucho stock baja (hasta -25%), con poco stock sube (hasta x2,5). Lo que **tú vendes** sube el stock y baja el precio; lo que compras lo baja y lo sube.
• **Límite de compra por objeto y semana:** **15 % del stock por jugador** y **40 % por gremio entero** (suma de todos sus miembros).
• Clic: comprar 1 · Shift+clic: 16 · Clic derecho: vender 1 · Shift+derecho: vender todo. También `/shop sell [n|all]` con el ítem en la mano.
• Los precios siguen el esfuerzo de conseguir cada objeto (minería, caza, crafteo): carbón 2, hierro 5, diamante 59, esquirla de eco 200, lingote de acero ≈20…

────────────────────────────────────────
## 📜 Misiones
```
/quest              Menú de misiones diarias
/quest weekly       3 misiones semanales (más grandes)
/quest chain        Historias en cadena (con NPC)
/quest chain start <id>  |  abandon <id>
/quest track        Marcador lateral con tu progreso
```
• **Diarias:** 5 al día según tu rango de misiones (matar, recolectar, pescar). Dan puntos de rango, monedas y XP de clase.
• **Semanales:** matar, recolectar, forjar y derrotar jefes.
• **Cadenas:** `bosque_maldito`, `forja_olvidada`, `sombras_del_nether`. Habla con su NPC o usa `/quest chain start <id>`.

────────────────────────────────────────
## 🏆 Rangos
Orden: **F · E · D · C · B · A · S · S+ · U (Ultimate)**. Hay rangos por **clase, subclase, trabajo, misiones y PvP**.
```
/rank [categoría] [perm|season]   Menú de ranking
/rank me                          Tus rangos
/rank top <cat> [n] [perm|season] Top de jugadores
/rank season                      Info de la temporada
/rank history                     Tus temporadas pasadas
```
**Permanentes (F … U):** no se reinician nunca; cuestan **5 veces más** puntos.
**De temporada (F-S1 … U-S1):** el número es la temporada. Empiezan de cero en cada temporada (30 días por defecto); al terminar queda guardado tu rango final y puedes recibir recompensas.
Puntos de temporada → F 0 · E 100 · D 300 · C 700 · B 1500 · A 3000 · S 6000 · S+ 12000 · U 25000
Ganas puntos al matar monstruos (jefes más), subir trabajos, completar misiones y ganar duelos.

────────────────────────────────────────
## 🔨 Recetas — Estaciones de trabajo
Cada **clase y subclase tiene su estación**; el equipo de su set (5 niveles × arma, casco, pechera, pantalones, botas y objeto secundario) **solo se forja en ella**. Se fabrica en una **mesa de crafteo** con este patrón:
```
 A B A
 B S B     S = centro, A/B = materiales de la firma
 A B A
```
• **Clase:** S = Mesa de herrería vanilla.
• **Subclase:** S = la **estación de su clase** (ítem `station_<clase>`).
• Pon la estación en el suelo y haz **clic derecho** para forjar. Con `/station <id>` ves las recetas sin estación.
Para forjar necesitas tener esa clase/subclase y nivel mínimo por tier: **T1 nv1 · T2 nv5 · T3 nv12 · T4 nv20 · T5 nv30**.

────────────────────────────────────────
## 🔨 Firma de cada estación de CLASE
```
Clase        A (4 esquinas)         B (4 lados)
Guerrero     Bloque de hierro       Escudo
Mago         Bloque de lapislázuli  Estantería
Cazador      Arco                   Diana
Pícaro       Perla de ender         Ojo de araña fermentado
Clérigo      Bloque de oro          Piedra luminosa
Nigromante   Arena de almas         Bloque de hueso
Druida       Bloque de musgo        Bayas dulces
Artífice     Bloque de redstone     Pistón
Bardo        Bloque musical         Cuerno de cabra
Brujo        Obsidiana              Ojo de ender
```
Ítem: `station_warrior`, `station_mage`, `station_hunter`, `station_rogue`, `station_cleric`, `station_necromancer`, `station_druid`, `station_artificer`, `station_bard`, `station_warlock`.

────────────────────────────────────────
## 🔨 Firma de cada estación de SUBCLASE
Centro = estación de su clase.
```
Paladín                  Manzana dorada + Bloque de oro
Bárbaro                  Hacha de hierro + Filete
Caballero de la Muerte   Rosa del Wither + Fragmento de netherita
Piroamante               Vara de blaze + Bloque de magma
Criomante                Hielo azul + Bloque de nieve
Electromante             Pararrayos + Bloque de cobre
Tirador                  Catalejo + Ballesta
Maestro de Bestias       Silla de montar + Rienda
Trampero                 Gancho de cuerda trampa + Telaraña
Asesino                  Espada de hierro + Tinte negro
Envenenador              Patata venenosa + Ojo de araña
Sombra / Duelista        Fragmento de eco + Sculk
Sacerdote de Luz         Piedra luminosa + Tótem de la inmortalidad
Inquisidor               Linterna de almas + Campana
Monje                    Bloque de bambú + Botella de miel
```
────────────────────────────────────────
## 🔨 Firma de subclases (2/2)
```
Señor de las Sombras     Cráneo de esqueleto + Bloque de carbón
Cosechador               Azada de hierro + Trigo
Plagador                 Carne podrida + Ojo de araña fermentado
Guardián Salvaje         Adoquín musgoso + Bayas dulces
Cazador Depredador       Hueso + Piel de conejo
Chamán Terrestre         Bloque de espeleotema + Arcilla
Maestro Alquimista       Soporte para pociones + Botella de cristal
Ingeniero                Repetidor + Tolva
Demoledor                TNT + Pólvora
Ladrador del Valor       Estandarte blanco + Zanahoria dorada
Maestro de la Discordia  Fragmento de disco 5 + Bola de fuego
Menestral                Campana + Hilo
Invocador Demoníaco      Bloque de verruga del Nether + Obsidiana llorona
Maldito                  Antorcha de almas + Obsidiana
Devorador de Almas       Lágrima de ghast + Tierra de almas
```
────────────────────────────────────────
## ⚒️ Materiales de forja (mesa de crafteo / alto horno)
```
Lingote de Acero    3 lingotes de hierro + 1 carbón (sin forma)
                    o alto horno: 1 bloque de hierro → 2 de acero
Lingote de Cobalto  2 acero + 2 lapislázuli + 1 lingote de cobre
Lingote Celeste     2 cobalto + 1 diamante + 1 membrana de fantasma
Núcleo de Dragón    3 celeste + 1 aliento de dragón + 1 fragmento de eco
```
**Costo de cada nivel del equipo** (por pieza, unidades: arma 2 · casco 3 · pechera 4 · pantalones 3 · botas 2 · secundario 1):
```
T1  pieza vanilla base + lingotes de hierro/cuero (unidades+1)
T2  pieza T1 + acero × unidades
T3  pieza T2 + cobalto × unidades
T4  pieza T3 + celeste × unidades
T5  pieza T4 + núcleo de dragón × (unidades/2, mín. 1)
```

────────────────────────────────────────
## 💎 Extras de equipo
• **Afijos:** el equipo de botín o forjado puede traer bonos aleatorios (crítico, robo de vida, maná...).
• **Gemas:** si un ítem tiene ranuras (T2 o más), haz **clic izquierdo con la gema en el cursor sobre el ítem** en tu inventario. 3 gemas iguales del mismo nivel se combinan en la mesa de crafteo en una de nivel superior.
• **Sets:** al llevar 2 / 4 / 6 piezas del mismo set se activan bonos (`/stats` los muestra).
• Algunos ítems exigen **clase y nivel**; si no los cumples vuelven a tu inventario.

────────────────────────────────────────
## 🛡️ Comandos de staff (op / `rpgleveltier.admin`)
```
/rlv userinfo <jugador> [sección]  Toda la info de un jugador
   secciones: all profile class stats abilities jobs quests ranks
              talents achievements gear social
/class @jugador   /ability @jugador   /jobs @jugador   /quest @jugador
/rank @jugador    /stats @jugador     (misma info por sección)
/rlv itemlist [categoría]   /rlv give <item>   /rlv roll   /rlv gem give
/rlv setpoints <j> <cat> <pts> [perm|season]   /rlv setclasslevel <j> <class|subclass> <nivel>
/rlv season [info|end|log]   /rlv event|boss|zone|npc ...   /rlv editor   /rlv debug
/rlv reload
```
