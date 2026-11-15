# Mixins

Takumi Gate usa un único mixin (un accessor de solo lectura); todo lo demás va por las APIs y eventos de Fabric.

| Mixin | Objetivo | Motivo | Riesgo de conflicto |
|---|---|---|---|
| `ServerLoginPacketListenerImplAccessor` | `ServerLoginPacketListenerImpl.authenticatedProfile` (campo privado) | Leer el perfil que Minecraft autenticó durante el login para comprobar que el ticket es de esa cuenta. Fabric API no ofrece un getter | Ninguno: es un `@Accessor` (no modifica código). Si el campo cambiase de nombre en una versión futura, el mixin falla en el primer login y lo detecta `TakumiGateLoginGameTest` |
