# Takumi Gate

[![Build](https://github.com/AloneX15/TakumiGate/actions/workflows/build.yml/badge.svg)](https://github.com/AloneX15/TakumiGate/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AloneX15/TakumiGate?sort=semver)](https://github.com/AloneX15/TakumiGate/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%2B%2026.x-62b47a)
![Loader](https://img.shields.io/badge/loaders-Fabric%20%7C%20Quilt%20%7C%20Forge%20%7C%20NeoForge-dbd0b4)

Lets only invited Takumi Launcher players join a private event server, and warns or kicks players when the event publishes a mandatory update.

Mod de servidor **y** cliente para las [instancias privadas de Takumi Launcher](https://github.com/AloneX15/TakumiLauncher):
eventos de Minecraft a los que solo entran los miembros invitados desde la web de Takumi.

## Características

- **Control de acceso al entrar:** durante el login el servidor pide al cliente el *ticket* que Takumi Launcher le dio
  al lanzar la instancia y lo comprueba con la web (`POST /api/gate/verify`). Se rechaza, antes de entrar al mundo, a
  quien no sea miembro, haya sido expulsado, use otra cuenta de Minecraft, no venga de Takumi Launcher o tenga una
  versión de la instancia que ya no vale.
- **Actualizaciones obligatorias:** el servidor consulta cada 30 s la web (`GET /api/gate/status`). Si el organizador
  publica una versión que exige reiniciar, los jugadores con la anterior reciben un título y un mensaje, recordatorios a
  10, 5 y 1 minutos, y se les desconecta al acabar el tiempo de gracia.
- **En el cliente:** muestra en el juego los avisos que manda Takumi Launcher, activa en directo los packs de recursos
  nuevos sin reiniciar, y `/takumi reiniciar` (o el botón «Reiniciar y actualizar» del launcher) cierra el juego para
  que el launcher lo vuelva a abrir actualizado.
- **Bloqueo de la carpeta de mods:** al llegar al menú abre todos los jars y avisa al launcher, que quita el permiso de
  lectura de `mods/` y `.fabric/processedMods/` mientras se juega (copiar los mods da «acceso denegado»).
- Solo actúa en **servidores dedicados configurados**: un mundo individual o LAN nunca queda bloqueado.

## Instalación

**Servidor del evento**

1. Instala el cargador del servidor. Fabric y Quilt necesitan también [Fabric API](https://modrinth.com/mod/fabric-api).
   Forge y NeoForge no necesitan Fabric API. En Arclight, identifica el cargador incluido en su distribución.
2. Instala en `mods/` un único jar de [Releases](https://github.com/AloneX15/TakumiGate/releases) que coincida con
   Minecraft y el cargador: `takumi_gate-fabric-…jar` para Fabric/Quilt, `takumi_gate-forge-…jar` para Forge o
   `takumi_gate-neoforge-…jar` para NeoForge. Arclight utiliza el jar de su cargador subyacente.

3. Arranca el servidor una vez: se crea `config/takumi_gate.json`.
4. En la web, *Instancias privadas → tu instancia → Ajustes → Servidor del evento (Takumi Gate)* → **Generar clave de
   servidor**. Copia la configuración que aparece (solo se muestra una vez) en `config/takumi_gate.json` y reinicia.
5. Mejor con `online-mode=true`: el ticket se comprueba contra la cuenta que valida Mojang. En `online-mode=false`
   (cuentas no premium) se comprueba por nombre.

**Jugadores:** sube el mismo jar a la carpeta `mods/` de la instancia en la web. Takumi Launcher lo instala con el
resto; los jugadores no tienen que hacer nada.

Build de desarrollo (último `main`, ya probado): [release `dev`](https://github.com/AloneX15/TakumiGate/releases/tag/dev).

## Compatibilidad

| Minecraft | Java | Fabric / Quilt | Forge | NeoForge |
|---|---|---|---|---|
| 1.21.1 | 21 | Jar `fabric` | Jar `forge` | Jar `neoforge` |
| 26.1.2 | 25 | Jar `fabric` | Jar `forge` | Jar `neoforge` |
| 26.2 | 25 | Jar `fabric` | Jar `forge` | Jar `neoforge` |
| 26.3 | 25 | Jar `fabric` | Jar `forge` | Jar `neoforge` |

Se genera un jar por cargador y versión. El protocolo de acceso es común: cliente y servidor utilizan el
jar de su propio cargador. Esto no elimina las restricciones de los demás mods de un servidor Forge/NeoForge.

Quilt utiliza el jar Fabric con Fabric API: las pruebas fijan Quilt Loader 0.30.2 para 1.21.1 y
0.31.0-beta.6 para 26.x. No se declara compatibilidad con cualquier versión del loader.

Arclight es un servidor híbrido con variantes Fabric, Forge y NeoForge. La matriz de pruebas fija
FeudalKings 1.0.1 (Fabric/NeoForge) y 1.0.2-SNAPSHOT-a3e2991 (Forge) para 1.21.1;
Dipper 1.1.0-SNAPSHOT para 26.1.2. No se declara soporte de
Arclight 26.2/26.3 sin una distribución upstream para probarlo. Véanse las
[releases oficiales](https://github.com/IzzelAliz/Arclight/releases) y el estado de validación en
[docs/compatibilidad.md](docs/compatibilidad.md).

El bloqueo de lectura de los jars conserva su implementación Fabric; Forge/NeoForge no emiten
`loaded.json`, para evitar bloquear archivos que sus cargadores puedan abrir posteriormente.
El ticket, los avisos, la recarga de packs y el reinicio tienen adaptadores para todos los cargadores.
No se garantiza un «100 %» para versiones futuras, plugins o combinaciones arbitrarias de mods.

Fabric se prueba además con Lithium y FerriteCore. Para reportar un conflicto, abre un
[issue](https://github.com/AloneX15/TakumiGate/issues) con Minecraft, cargador, distribución y log.

## Configuración

`config/takumi_gate.json` (servidor):

| Clave | Por defecto | Qué hace |
|---|---|---|
| `enabled` | `true` | `false` deja entrar a todo el mundo |
| `api_url` | `https://takumilabs.org` | Web de Takumi (`http` solo para `localhost`) |
| `server_key` | `""` | Clave `tgk_…` de la instancia. Sin ella el mod no hace nada |
| `poll_seconds` | `30` | Cada cuánto se buscan versiones nuevas (10–600) |
| `allow_when_api_down` | `false` | Dejar entrar si la web no responde (por defecto se rechaza) |
| `bypass_uuids` | `[]` | UUID de cuentas que entran sin ticket (staff sin Takumi Launcher) |

Si el archivo está dañado se guarda una copia `.bak` y se regenera con valores por defecto (el gate queda apagado
hasta volver a poner la clave). Detalles del protocolo: [docs/protocolo.md](docs/protocolo.md).

### Límites

- No impide copiar los mods de un jugador que ya los tiene en su PC; eso lo complica Takumi Launcher (almacén cifrado y
  marca de agua). Lo que el servidor garantiza es que **solo juegan los miembros** y **con la versión publicada**.
- Un cliente modificado podría responder al login con un ticket robado, pero solo sirve para la cuenta de Minecraft a la
  que se emitió y caduca a las 12 horas.

## Desarrollo

```bash
# Fabric: unitarios y gametests de servidor
./gradlew :1.21.1:build :26.1.2:build :26.2:build :26.3:build
# Forge / NeoForge: mismo sufijo para cada Minecraft
./gradlew :1.21.1-forge:build :1.21.1-neoforge:build
./gradlew :26.3-forge:build :26.3-neoforge:build
# Compat pack en una ejecución independiente
./gradlew :1.21.1:runGameTest :26.3:runGameTest -PcompatPack
# Tests de cliente disponibles en 26.x
./gradlew :26.3:runClientGameTest
```

Jars en `versions/<destino>/build/libs/`. No instales `*-sources.jar` ni `*-dev.jar`.
Fabric 1.21.1 se remapea a intermediary; Forge/NeoForge usan nombres oficiales de Mojang.

La CI prueba el login contra servidores reales con una API local, sin credenciales reales de la web:
cliente sin mod, ticket vacío, protocolo incorrecto, formato inválido, cuenta denegada, actualización
obligatoria, API caída, payload sobrante, ticket válido y bypass.

```bash
python3 scripts/smoke_login.py --run-dir versions/26.3-neoforge/run -- ./gradlew :26.3-neoforge:runServer --console=plain
python3 scripts/prepare_test_server.py --platform quilt --loader fabric --minecraft 1.21.1
python3 scripts/smoke_login.py --run-dir build/server-tests/quilt-fabric-1.21.1 --cwd build/server-tests/quilt-fabric-1.21.1 -- java -jar server-launch.jar nogui
```

Usa directorios desechables y el Java del juego. El arnés rechaza sobrescribir una configuración Gate
que no haya creado él mismo. Los clientes Forge/NeoForge tienen un smoke test que abre el juego, comprueba las traducciones y
carga los mixins de login y comandos antes de cerrar. Actívalo con `-PgateClientSmoke` al ejecutar `runClient`.
No sustituye una prueba completa de partida con todos los plugins y mods de cada evento.

## Licencia

[MIT](LICENSE).

---

Creado por **TakumiStudios**.
