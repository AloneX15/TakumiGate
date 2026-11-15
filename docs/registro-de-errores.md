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
