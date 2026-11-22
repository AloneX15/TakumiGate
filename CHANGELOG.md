# Changelog

## 0.1.0

Primera versión estable.

**Servidor del evento**

- Control de acceso con el ticket de Takumi Launcher durante el login (servidores dedicados con `server_key`): solo
  entran los miembros de la instancia, con su cuenta de Minecraft y con una versión de la instancia que siga valiendo.
- Servidores `online-mode=false`: el ticket se comprueba por nombre (las cuentas offline de Takumi tienen UUID aleatorio).
- Siempre se rechaza cuando la web niega el acceso, también si su respuesta llega mal formada o es otro error `4xx`.
  `allow_when_api_down` solo actúa si la web no responde (red, `5xx`, `408`, `429`) o rechaza la `server_key`.
- Actualizaciones obligatorias: aviso, recordatorios a 10/5/1 min y desconexión al acabar el tiempo de gracia.
- El log avisa una vez cuando no se puede consultar el estado de la instancia (p. ej. `server_key` incorrecta) y otra
  cuando se recupera.

**Cliente**

- Avisos de Takumi Launcher en el juego, packs de recursos en directo y `/takumi reiniciar`.
- Un fallo puntual al leer las señales del launcher se reintenta en vez de desactivar los avisos.
- Fabric/Quilt: abre todos los jars al arrancar y avisa al launcher para que bloquee la carpeta de mods mientras se juega.

**Plataformas**

- Jars separados para Fabric/Quilt, Forge y NeoForge en Minecraft 1.21.1, 26.1.2, 26.2 y 26.3.
- Barrera de login común y pruebas de acceso en servidores híbridos Arclight.
- Matriz de CI por cargador, compat pack (Lithium + FerriteCore) y pruebas del jar distribuible en Quilt y Arclight.
