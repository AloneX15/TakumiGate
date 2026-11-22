# Registro de errores

## 2026-11-15 · La petición a la web se queda colgada con `http://`

- **Síntoma:** contra una web en `http://localhost` (desarrollo), la llamada a la API no respondía nunca y el login se
  quedaba esperando. Al matar el proceso, el servidor de desarrollo de Vite se caía con `ECONNRESET`.
- **Causa:** `java.net.http.HttpClient` intenta HTTP/2 por defecto; en `http://` lo hace con `Upgrade: h2c`, que el
  servidor de Node no atiende. Con `https` no pasa (se negocia por ALPN).
- **Solución:** `GateApi` fuerza `HttpClient.Version.HTTP_1_1`; todas las peticiones llevan timeout de 10 s.
- **Test:** `TakumiGateLoginGameTest` (login real contra una web local por `http`).

## 2026-11-15 · `PacketByteBufs` no existe en la Fabric API de 26.3

- **Síntoma:** no compilaba `PacketByteBufs.create()`.
- **Causa:** la clase ya no está en `fabric-networking-api-v1` de la Fabric API 0.166.
- **Solución:** `new FriendlyByteBuf(Unpooled.buffer())`, que existe en todas las versiones.
- **Test:** la compilación de 26.1.2, 26.2 y 26.3.

## 2026-11-15 · Jugadores con cuenta offline rechazados en servidores `online-mode=false`

- **Síntoma:** en un servidor sin autenticación, el gate rechazaba con «Este ticket pertenece a otra cuenta» a todos
  los jugadores con cuenta offline de Takumi Launcher.
- **Causa:** el ticket se comprobaba por UUID; el servidor offline calcula el UUID a partir del nombre y Takumi Launcher
  da a sus cuentas offline un UUID aleatorio.
- **Solución:** en `online-mode=false` el mod envía el nombre y la web compara por nombre (sin distinguir mayúsculas).
- **Test:** `e2e_private_instance` del launcher (partida real con cuenta offline) y `TakumiGateLoginGameTest`.

## Compatibilidad entre cargadores

- **Arclight avanzaba el login antes del rechazo asíncrono de Fabric.** Se añade una barrera común en
  VERIFYING; Fabric API entrega el veredicto a esa barrera y la desconexión se realiza al resolverla en
  el hilo del servidor. Regresión: `scripts/smoke_login.py`, sin mod y con ticket vacío/denegado/caducado.
- **El decoder nativo leía el marcador nullable como versión de protocolo.** `readPayload` recibe también
  el booleano que indica si hay respuesta. Se consume antes del protocolo y se rechazan datos sobrantes.
  Regresión: clientes sin mod, protocolo distinto, payload sobrante y ticket válido en el smoke test.
- **Los tests unitarios de NeoForge no recibían las bibliotecas del juego.** Se declaran Gson y SLF4J solo
  para el runtime de JUnit; no se incluyen en el jar del mod. Regresión: los doce tests comunes en cada nodo.

- **Arclight Forge 1.0.1 no arranca por un mixin propio.** Se reproduce retirando Gate del directorio
  `mods/`. La distribución oficial nightly 1.0.2-SNAPSHOT-a3e2991 arranca y supera los diez escenarios;
  el instalador de pruebas fija su URL y SHA-256. No se anuncia soporte del binario 1.0.1 roto.
- **ForgeGradle 7 y Gradle 9 separaban los destinos reales de compilación de los del mod de desarrollo.**
  Podía faltar `AnswerDecoderMixin` al arrancar o no encontrarse las clases comunes al compilar JUnit.
  Se unifican explícitamente los destinos del compilador en `build/sourceSets/<sourceSet>`. Regresión:
  build, login real y smoke de cliente en las cuatro versiones Forge.

## 2026-11-22 · Un rechazo mal formado podía dejar entrar con `allow_when_api_down`

- **Síntoma:** una respuesta `403` con `"error": null` (u otro tipo que no fuera texto) lanzaba una excepción al
  leerla; el error acababa como «web no disponible» y, con `allow_when_api_down: true`, el jugador entraba.
- **Causa:** `GateApi.parseVerify` usaba `getAsString`/`getAsBoolean` sin comprobar el tipo, y el
  `exceptionally` de la petición convertía cualquier excepción en `Unavailable`.
- **Solución:** `parseVerify` nunca lanza: lee los campos comprobando el tipo y todo `403` u `ok: false` es un rechazo.
- **Test:** `TakumiGateTest.malformedRefusalsAreStillDenials`.

## 2026-11-22 · Un nombre no válido en un servidor offline podía entrar con `allow_when_api_down`

- **Síntoma:** en `online-mode=false`, un jugador con un nombre como `Pepe.X` (válido para el servidor, no para la web)
  y un ticket inventado recibía `400 Falta uuid o name.` de la web; el mod lo trataba como «web caída» y, con
  `allow_when_api_down: true`, le dejaba entrar.
- **Causa:** todo estado distinto de `200`/`403` se consideraba indisponibilidad.
- **Solución:** cualquier `4xx` salvo `401`, `408` y `429` es un rechazo. La caída de la web sigue siendo red, `5xx`,
  `408` o `429`.
- **Test:** `TakumiGateTest.clientErrorsAreDenialsNotOutages`; reproducido contra la web local con `curl`.
