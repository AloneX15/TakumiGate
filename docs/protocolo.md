# Protocolo de Takumi Gate

Cómo se hablan el launcher, el juego, el servidor del evento y la web de Takumi. El contrato completo de la web está en
`takumi-webpage/docs/launcher-integration.md` §7.

## 1. Ticket de entrada

1. Antes de lanzar una instancia privada, Takumi Launcher pide un ticket a la web:
   `POST /api/launcher/instances/:id/ticket` con `{ uuid, name, version }`. Queda ligado al usuario de Takumi, a la cuenta
   de Minecraft (`uuid`) y a la versión de la instancia que se acaba de instalar. Dura 12 horas y se puede reutilizar al
   reconectar.
2. El launcher lo pasa al juego en la variable de entorno `TAKUMI_GATE_TICKET` (nunca en la línea de comandos), junto
   con `TAKUMI_INSTANCE_VERSION`.
3. Durante el login, el servidor manda una *login query* por el canal `takumi_gate:ticket` con `[protocolo: byte]`. El
   cliente responde `[protocolo: byte][ticket: string UTF-8, máx. 144]`. Un cliente sin el mod no entiende la query y
   se le rechaza.
4. El servidor comprueba el ticket con `POST /api/gate/verify` (`Authorization: Bearer <server_key>`). En servidores
   `online-mode=true` envía `{ ticket, uuid }` con el perfil que **Minecraft** autenticó (no lo que diga el cliente); en
   `online-mode=false` envía `{ ticket, name }`, porque allí el UUID sale del nombre y las cuentas offline de Takumi
   Launcher tienen UUID aleatorio. El login espera a la respuesta.
   - `200 { ok: true, username, version, current_version, restart_required }` → entra, salvo que
     `restart_required` (su versión ya no vale) → se le rechaza.
   - `403 { ok: false, error }` → se le rechaza con `error` (no es miembro, ticket caducado, otra cuenta, suspendido…).
   - Otro `4xx` (p. ej. `400` por un nombre que no es de Minecraft en un servidor offline) → se le rechaza siempre.
   - Error de red, `5xx`, `408`, `429` o `401` (clave mal puesta) → se le rechaza con «No se ha podido comprobar tu
     acceso», salvo `allow_when_api_down: true`.

## 2. Actualizaciones obligatorias

- El servidor consulta `GET /api/gate/status` cada `poll_seconds`: devuelve las últimas versiones con
  `restart_required`, `grace_minutes` y `published_at`.
- Para cada jugador se calcula el plazo: la menor `published_at + grace_minutes` de las versiones posteriores a la suya
  que piden reinicio (`UpdatePolicy`). Las versiones que solo cambian packs de recursos no obligan a nadie a salir.
- Aviso al detectarlo (título + chat), recordatorios a 10, 5 y 1 minutos (barra de acción) y desconexión al vencer el
  plazo. Con gracia 0 la desconexión es inmediata (en ≤ 15 s: consulta + comprobación cada 5 s).

## 3. Señales del launcher al juego

Mientras el juego corre, Takumi Launcher escribe en `<juego>/.takumi/` y el mod lo lee cada 2 s:

| Archivo | Lo escribe | Efecto en el juego |
|---|---|---|
| `notice.json` `{ version, grace_minutes, deadline, notes }` | El launcher, al ver una versión que pide reiniciar | Mensaje en el chat con «[Reiniciar y actualizar]» y recordatorio cada minuto |
| `reload.json` `{ version, packs: ["file/X.zip"] }` | El launcher, tras aplicar una versión que solo cambia packs | Activa esos packs y recarga los recursos, sin reiniciar |
| `quit.request` | El launcher («Reiniciar y actualizar») o `/takumi reiniciar` | Cierra el juego limpiamente |
| `loaded.json` | **El juego**, al llegar al menú, tras abrir todos los jars de `mods/` y `.fabric/processedMods/` | El launcher quita el permiso de lectura de esas carpetas hasta que el juego se cierre |
| `restart.request` | Igual que `quit.request` | El launcher vuelve a lanzar la instancia actualizada al cerrarse el juego. Si el juego no se cierra en 20 s, lo cierra el launcher |

Los IDs de pack se validan (`file/<nombre>`, sin `/`, `\` ni `..`) y los archivos de más de 64 KB se ignoran.


## 4. Adaptadores de cargador

Todos conservan `takumi_gate:ticket` y protocolo 1. La query se envía cuando Minecraft ya tiene el
perfil autenticado; VERIFYING queda bloqueado hasta resolver la verificación. Fabric y Quilt usan
los decodificadores y el registro de queries de Fabric API y entregan la respuesta a la barrera común.
Forge y NeoForge decodifican únicamente la transacción negativa reservada de Gate. Las otras queries
conservan el comportamiento de su cargador. Arclight utiliza el adaptador de su cargador subyacente.

La respuesta de login vanilla lleva un booleano nullable antes del protocolo: `false` si el cliente
no entiende el canal; `true` seguido de `[protocolo][ticket]` si lo entiende. Se rechazan datos
sobrantes y tickets de tamaño o formato incorrectos antes de llamar a la web.

Forge y NeoForge no emiten `loaded.json`: su apertura diferida de archivos no se considera compatible
con el bloqueo de lectura del launcher. Las demás señales mantienen el mismo contrato.
