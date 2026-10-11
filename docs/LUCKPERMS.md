# Rangos de LuckPerms (RPGLeVeL)

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
- Clase: `lp user <jugador> parent add class_<claseid>` (ids: artificie, bardo, brujo, cazador, clerigo, druida, guerrero, mago, nigromante, picaro). Al cambiar de clase quita el anterior: `lp user <jugador> parent remove class_<viejo>`.
- Pago: `lp user <jugador> parent add vip_<id>` (ids: bronce, plata, oro, platino, esmeralda, rubi, zafiro, amatista, diamante, mitico). Al subir de escalón quita el anterior: `lp user <jugador> parent remove vip_<viejo>` (o usa el track: `lp user <jugador> promote pago`).

## 4. Tabla
| Grupo | Peso | Prefijo (prioridad) | Permisos propios |
|---|---|---|---|
| class_artificie | 100 | &6[Artífice] (50) | playerkits.kit.clase_artificie |
| class_bardo | 100 | &d[Bardo] (50) | playerkits.kit.clase_bardo |
| class_brujo | 100 | &5[Brujo] (50) | playerkits.kit.clase_brujo |
| class_cazador | 100 | &a[Cazador] (50) | playerkits.kit.clase_cazador |
| class_clerigo | 100 | &f[Clérigo] (50) | playerkits.kit.clase_clerigo |
| class_druida | 100 | &2[Druida] (50) | playerkits.kit.clase_druida |
| class_guerrero | 100 | &c[Guerrero] (50) | playerkits.kit.clase_guerrero |
| class_mago | 100 | &b[Mago] (50) | playerkits.kit.clase_mago |
| class_nigromante | 100 | &8[Nigromante] (50) | playerkits.kit.clase_nigromante |
| class_picaro | 100 | &e[Pícaro] (50) | playerkits.kit.clase_picaro |
| vip_bronce | 200 | &6[Bronce] (100) | essentials.chat.color, essentials.nick, playerkits.kit.vip_bronce |
| vip_plata | 210 | &7[Plata] (101) | essentials.hat, playerkits.kit.vip_plata |
| vip_oro | 220 | &e[Oro] (102) | essentials.workbench, playerkits.kit.vip_oro |
| vip_platino | 230 | &f[Platino] (103) | essentials.enderchest, playerkits.kit.vip_platino |
| vip_esmeralda | 240 | &a[Esmeralda] (104) | essentials.feed, playerkits.kit.vip_esmeralda |
| vip_rubi | 250 | &c[Rubí] (105) | essentials.repair, playerkits.kit.vip_rubi |
| vip_zafiro | 260 | &9[Zafiro] (106) | essentials.heal, playerkits.kit.vip_zafiro |
| vip_amatista | 270 | &5[Amatista] (107) | essentials.anvil, playerkits.kit.vip_amatista |
| vip_diamante | 280 | &b&l[Diamante] (108) | playerkits.kit.vip_diamante |
| vip_mitico | 290 | &d&l[Mítico] (109) | playerkits.kit.vip_mitico |

Los de pago heredan al anterior: Mítico tiene todo lo de Bronce…Diamante. Son comodidades y cosméticos, no ventajas de combate.
Los nodos de Essentials (`essentials.*`) y `playerkits.kit.<kit>` son los habituales de esos plugins; compruébalos en tu servidor con `/lp verbose on` o `/lp user <jugador> permission check <nodo>`.
