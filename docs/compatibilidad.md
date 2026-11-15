# Compatibilidad verificada

Validación local del 15 de noviembre de 2026. Los cargadores y servidores se fijan por versión en los
scripts y en la CI; esta matriz no promete compatibilidad con cualquier versión futura o modpack.

| Minecraft | Fabric | Quilt | Forge | NeoForge | Java del juego |
|---|---|---|---|---|---|
| 1.21.1 | Compilación, unitarios y servidor | Login del jar distribuible | Compilación, unitarios, login y arranque de cliente | Compilación, unitarios, login y arranque de cliente | 21 |
| 26.1.2 | Compilación, unitarios y servidor | Login del jar distribuible | Compilación, unitarios, login y arranque de cliente | Compilación, unitarios, login y arranque de cliente | 25 |
| 26.2 | Compilación, unitarios y servidor | Login del jar distribuible | Compilación, unitarios, login y arranque de cliente | Compilación, unitarios, login y arranque de cliente | 25 |
| 26.3 | Compilación, unitarios y servidor | Login del jar distribuible | Compilación, unitarios, login y arranque de cliente | Compilación, unitarios, login y arranque de cliente | 25 |

Se ejecutan los doce tests unitarios comunes por destino: 144 comprobaciones entre los doce jars.
Fabric 26.x conserva sus gametests de cliente; Forge/NeoForge abren el juego, comprueban traducciones,
cargan los mixins de login y comandos, y cierran con un resultado JSON comprobable por la CI.

El arnés de servidor utiliza `online-mode=false` y una API HTTP local. Comprueba diez escenarios:
cliente sin mod, ticket vacío, protocolo distinto, ticket mal formado, cuenta denegada, instancia
desactualizada, API indisponible, datos sobrantes, acceso válido y bypass. También verifica la
identidad enviada y que las entradas inválidas no llamen a la API. El E2E contra la web real requiere
las variables `TAKUMI_GATE_E2E_*` y no se presenta como ejecutado sin esas credenciales.

## Arclight

| Minecraft | Variante | Distribución probada | Resultado de login |
|---|---|---|---|
| 1.21.1 | Fabric | FeudalKings 1.0.1 / 8ec9529 | Diez escenarios correctos |
| 1.21.1 | Forge | FeudalKings 1.0.2-SNAPSHOT / a3e2991 | Diez escenarios correctos |
| 1.21.1 | NeoForge | FeudalKings 1.0.1 / 8ec9529 | Diez escenarios correctos |
| 26.1.2 | Fabric | Dipper 1.1.0-SNAPSHOT / 9e3febe | Diez escenarios correctos |
| 26.1.2 | Forge | Dipper 1.1.0-SNAPSHOT / 9e3febe | Diez escenarios correctos |
| 26.1.2 | NeoForge | Dipper 1.1.0-SNAPSHOT / 9e3febe | Diez escenarios correctos |

La release Forge FeudalKings 1.0.1 falla en `MinecraftServerMixin.arclight$worldInit`, incluso al
arrancarla sin Takumi Gate. La nightly indicada supera el arranque y el login; su descarga se fija
por objeto y SHA-256 en `scripts/prepare_test_server.py`. No se marca 1.0.1 Forge como compatible.
Las fuentes upstream son las [releases](https://github.com/IzzelAliz/Arclight/releases) y el
[sitio oficial de descargas](https://arclight.izzel.io/).

No se anuncia Arclight 26.2/26.3 sin una distribución upstream disponible para validar.

## Instalación y límites

- Fabric y Quilt usan el jar `fabric` y necesitan Fabric API. Quilt se valida con 0.30.2 en 1.21.1 y
  0.31.0-beta.6 en 26.x.
- Forge y NeoForge tienen jars propios. Arclight utiliza el jar del cargador incluido en su distribución.
- Los clientes usan el jar de su cargador; compartir el protocolo de Gate no elimina las restricciones
  de otros mods sobre qué clientes pueden entrar a un servidor.
- El bloqueo de lectura de los jars sigue siendo específico de Fabric. Forge/NeoForge no emiten la
  señal `loaded.json`, porque bloquear sus archivos diferidos puede romper la carga de clases o recursos.
- Los smoke tests de cliente verifican el arranque y los hooks; no sustituyen una partida completa con
  el launcher, todos los plugins del evento, las señales de reinicio y un servicio web real.

Los workflows incluyen esta matriz y validación con `actionlint`. Los resultados locales no equivalen
a afirmar que se ha ejecutado o publicado una nueva build en GitHub.
