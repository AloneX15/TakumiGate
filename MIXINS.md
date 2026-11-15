# Mixins de Takumi Gate

El control de acceso comparte una barrera en el login vanilla entre Fabric, Quilt, Forge y NeoForge.
En Fabric y Quilt se mantienen los decodificadores y el registro de queries de Fabric API. La barrera
impide que Arclight avance al mundo antes de resolver la validación; desconectar desde una continuación
asíncrona de Fabric podía permitir avanzar el login antes del rechazo.

| Mixin | Destino | Motivo y alcance |
|---|---|---|
| `NativeLoginMixin` | `ServerLoginPacketListenerImpl.verifyLoginAndFinishConnectionSetup`, `handleCustomQueryPacket` | Conserva VERIFYING hasta resolver el ticket del perfil autenticado. Solo servidores dedicados configurados. |
| `AnswerDecoderMixin` | `ServerboundCustomQueryAnswerPacket.readPayload` | En Forge/NeoForge conserva únicamente nuestra respuesta. Incluye el marcador nullable y limita tamaño y datos sobrantes. |
| `QueryDecoderMixin` | `ClientboundCustomQueryPacket.readPayload` | En Forge/NeoForge conserva únicamente `takumi_gate:ticket`; deja las otras queries al cargador. |
| `NativeClientLoginMixin` | `ClientHandshakePacketListenerImpl.handleCustomQuery` | Responde únicamente al canal de Takumi Gate; cliente de todos los cargadores. |
| `NativeServerMixin` | `MinecraftServer.runServer`, `stopServer` | Inicio y cierre en Forge/NeoForge. Fabric/Quilt usan eventos de Fabric API. |
| `NativePlayerListMixin` | `PlayerList.remove` | Libera estado de jugadores en Forge/NeoForge. |
| `NativeClientLifecycleMixin` | `Minecraft.run`, `close` | Inicia y cierra las señales del launcher en Forge/NeoForge. |
| `NativeClientCommandMixin` | `ClientPacketListener.sendCommand` | Intercepta solo `takumi reiniciar` en Forge/NeoForge. |

No hay overwrites ni redirects. Las queries nativas utilizan un ID negativo reservado para no ocupar
los IDs secuenciales de FML/Fabric. Los hooks de acceso son obligatorios (`defaultRequire: 1`): si cambia
un objetivo, el arranque debe fallar claramente en lugar de dejar el servidor abierto silenciosamente.
Un mod que reemplace el login o cambie sus estados puede entrar en conflicto; las pruebas de login en
servidores reales detectan tanto el rechazo incorrecto como el avance sin autorización.

Minecraft 1.21.1 Fabric se remapea a intermediary con Loom; Forge/NeoForge usan nombres oficiales de
Mojang. No se remapea Forge 1.21.1 a SRG. Las versiones 26.x no están ofuscadas.
