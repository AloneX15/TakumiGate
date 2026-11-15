# Takumi Gate

[![Build](https://github.com/AloneX15/TakumiGate/actions/workflows/build.yml/badge.svg)](https://github.com/AloneX15/TakumiGate/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AloneX15/TakumiGate?sort=semver)](https://github.com/AloneX15/TakumiGate/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-26.x-62b47a)
![Loader](https://img.shields.io/badge/loader-Fabric-dbd0b4)

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

1. Instala [Fabric Loader](https://fabricmc.net/use/) y [Fabric API](https://modrinth.com/mod/fabric-api).
2. Descarga el jar de tu versión de Minecraft desde [Releases](https://github.com/AloneX15/TakumiGate/releases) y ponlo en `mods/`.

3. Arranca el servidor una vez: se crea `config/takumi_gate.json`.
4. En la web, *Instancias privadas → tu instancia → Ajustes → Servidor del evento (Takumi Gate)* → **Generar clave de
   servidor**. Copia la configuración que aparece (solo se muestra una vez) en `config/takumi_gate.json` y reinicia.
5. Mejor con `online-mode=true`: el ticket se comprueba contra la cuenta que valida Mojang. En `online-mode=false`
   (cuentas no premium) se comprueba por nombre.

**Jugadores:** sube el mismo jar a la carpeta `mods/` de la instancia en la web. Takumi Launcher lo instala con el
resto; los jugadores no tienen que hacer nada.

Build de desarrollo (último `main`, ya probado): [release `dev`](https://github.com/AloneX15/TakumiGate/releases/tag/dev).

## Compatibilidad

| Minecraft | Estado |
|---|---|
| 26.1.x | ✅ |
| 26.2.x | ✅ |
| 26.3.x | ✅ |

Probado en la CI junto a Lithium y FerriteCore. Si encuentras un conflicto con otro mod, abre un
[issue](https://github.com/AloneX15/TakumiGate/issues).

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
./gradlew :26.3:build              # compila y ejecuta los tests unitarios
./gradlew :26.3:runGameTest        # gametests de servidor
./gradlew :26.3:runClientGameTest  # test de cliente con capturas
TAKUMI_INSTANCE_VERSION=1 ./gradlew :26.3:runClientGameTest   # + avisos y packs en directo
# + login real contra una web local (ver TakumiGateLoginGameTest):
TAKUMI_GATE_E2E_API=http://[::1]:5173 TAKUMI_GATE_E2E_KEY=tgk_… TAKUMI_GATE_E2E_INSTANCE=<id>   TAKUMI_GATE_E2E_TOKEN=<token> TAKUMI_GATE_E2E_KICK=1 ./gradlew :26.3:runClientGameTest
./gradlew :26.3:runClient          # abre el juego
```

## Licencia

[MIT](LICENSE).

---

Creado por **TakumiStudios**.
