# Changelog

## 0.1.0

- Builds separadas para Fabric/Quilt, Forge y NeoForge en 1.21.1 y 26.x.
- Barrera de login común y pruebas de acceso para servidores híbridos Arclight.
- Matriz de CI por cargador y pruebas del jar distribuible en Quilt y Arclight.
- Una respuesta de rechazo mal formada de la web se trata siempre como rechazo (antes podía contar como «web caída» y
  dejar entrar con `allow_when_api_down: true`). Lo mismo con cualquier otro error `4xx` de la web, como el `400`
  que devuelve para nombres que no son de Minecraft en servidores `online-mode=false`.
- El log avisa una vez cuando no se puede consultar el estado de la instancia (p. ej. `server_key` incorrecta).
- Cliente: un fallo puntual al leer las señales del launcher se reintenta en vez de desactivar los avisos.
- Control de acceso con el ticket de Takumi Launcher durante el login (servidores dedicados con `server_key`).
- Actualizaciones obligatorias: aviso, recordatorios a 10/5/1 min y desconexión al acabar el tiempo de gracia.
- Cliente: avisos de Takumi Launcher en el juego, packs de recursos en directo y `/takumi reiniciar`.
- Cliente: abre todos los jars al arrancar y avisa al launcher para que bloquee la carpeta de mods mientras se juega.
- Servidores `online-mode=false`: el ticket se comprueba por nombre (las cuentas offline de Takumi tienen UUID aleatorio).
