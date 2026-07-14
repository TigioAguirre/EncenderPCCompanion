# Cómo contribuir

¡Gracias por tu interés en mejorar EncenderPC companion! Esta guía
resume el flujo esperado para reportar errores, proponer features y
enviar pull requests.

## Antes de empezar

- Este es un proyecto chico, mantenido en tiempo libre — las
  respuestas pueden tardar.
- Leé el [código de conducta](CODE_OF_CONDUCT.md); se aplica en
  issues, PRs y cualquier otro espacio del proyecto.
- Revisá los [issues abiertos](../../issues) para evitar duplicar
  trabajo.

## Reportar un bug

Abrí un issue usando la plantilla de "Bug report" e incluí, si es
posible:

- Versión de Android y modelo del teléfono.
- Modelo de pulsera/reloj (Mi Band 6, 7, 8...).
- Pasos para reproducir el problema.
- Qué esperabas que pasara vs. qué pasó.
- Logs relevantes (`adb logcat | grep -i bandcolorreact`) si aplica.

## Proponer una función nueva

Abrí un issue con la plantilla de "Feature request" **antes** de
ponerte a programar, así se puede discutir el enfoque y evitar
trabajo que después no se pueda mergear. Para features grandes (por
ejemplo la detección de si la PC está encendida), mejor coordinar
primero en el issue cómo se va a organizar el código.

## Enviar un Pull Request

1. Hacé fork del repo y creá una rama descriptiva:
   `git checkout -b fix/nombre-del-cambio`.
2. Seguí la organización por paquetes existente
   (`ui/`, `service/`, `overlay/`, `data/`) — si tu cambio no encaja
   en ninguno, decilo en el PR y lo vemos juntos.
3. Mantené el estilo del código existente:
   - Kotlin idiomático, sin agregar dependencias pesadas sin
     discutirlo antes.
   - Strings de UI en `res/values/strings.xml`, nunca hardcodeados en
     el `.kt` (para que se puedan traducir).
   - Colores en `res/values/colors.xml`, no hex sueltos en los
     layouts.
   - Reusar los estilos de texto (`Text.Title`, `Text.Subtitle`,
     `Text.Body`, `Text.Caption`) y de botón (`Button.Primary`,
     `Button.Secondary`, `Button.Text`) en vez de definir tamaños o
     tipografías ad-hoc.
4. Verificá que compila antes de abrir el PR:
   ```bash
   ./gradlew assembleDebug lintDebug
   ```
5. Escribí un mensaje de commit claro (en español o inglés, cualquiera
   de los dos está bien, pero sé consistente dentro del mismo commit).
6. Abrí el PR contra `main` describiendo **qué** cambia y **por qué**.
   Si soluciona un issue, enlazalo con `Closes #123`.

## Traducciones

Si querés traducir la app a otro idioma, copiá
`app/src/main/res/values/strings.xml` a `app/src/main/res/values-<código-de-idioma>/strings.xml`
(por ejemplo `values-en` para inglés) y traducí únicamente los
valores, no los `name=`.

## Diseño / UI

La app sigue una paleta oscura ("Xiaomi Tech") definida en
`colors.xml` y tipografía Manrope (ver `NOTICE`). Si proponés cambios
visuales, tratá de mantener consistencia con las heurísticas de
usabilidad ya aplicadas: visibilidad del estado del sistema, mismos
íconos/labels entre el tutorial y la pantalla principal, prevención de
errores con validación en tiempo real, etc.
