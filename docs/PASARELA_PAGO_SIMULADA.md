# Reserva y pago de prueba

En Comedores, el carrusel muestra **hoy y los cuatro días laborables siguientes**,
saltando sábados y domingos en las cuatro páginas futuras. Se puede reservar
cualquiera de esos cuatro menús, pero no el de hoy. Si hoy cae en fin de semana,
su página se mantiene como «Sin servicio».

El botón **Reservar** abre una pantalla con el día, el tipo de menú,
el importe de la reserva para llevar (4,00 €) y dos
métodos visuales de prueba: Bizum y tarjeta. No se piden datos bancarios ni se
llama a una API de pagos o de reservas.
Los textos visibles de la aplicación no incluyen avisos de simulación; esta
documentación describe su funcionamiento técnico. El precio se comparte entre
el menú, la pasarela y el diálogo nativo mediante `DiningReservationPolicy`.

Al pulsar **Proceder al pago**, Android solicita la autenticación configurada en
el móvil. En Android 10 o posterior se usa el diálogo nativo con biometría o
credencial del dispositivo; en Android 9 se ofrece la alternativa de PIN/patrón,
y en Android 8 se utiliza el bloqueo del dispositivo. Los métodos biométricos
disponibles dependen del hardware y de la configuración del teléfono. La app
solo recibe el resultado, nunca el PIN, patrón, contraseña, huella o imagen facial.

Solo una autenticación correcta guarda la reserva de demostración localmente.
El menú elegido pasa a verde y el botón muestra **Reservado**. Se mantiene una
reserva por día, también al volver a abrir la aplicación. No es una reserva real
del comedor y no se realiza ningún cargo. Los días anteriores se eliminan del
estado local, como en la maqueta previa.

Cancelar, fallar o no tener bloqueo configurado no crea una reserva. También se
ignoran los resultados duplicados y los que lleguen después de salir de la
pasarela. Si se recrea la pantalla durante la autenticación, hay que volver a
confirmar; nunca se da por completada por restaurar la interfaz.
La fecha se comprueba al abrir el pago, antes de autenticar y al recibir el
resultado: si se cruza la medianoche y el menú pasa a ser el de hoy, no se reserva.

## Comprobación en el móvil

1. Abrir Comedores: debe mostrarse hoy y su botón debe estar desactivado.
   Deslizar a uno de los cuatro días siguientes, pulsar **Reservar** y comprobar
   el tipo de menú y los 4,00 €.
2. Pulsar **Proceder al pago** y cancelar el diálogo del sistema: no debe reservar.
3. Reintentar y autenticarse: debe aparecer **Reservado**, con el menú en verde.
4. Cerrar y abrir la app: la reserva de prueba debe seguir visible para ese día.
5. Consultar otro día: debe conservar su propio estado, sin heredar la reserva.

`DiningPaymentAuthorizerTest` comprueba la autorización y los resultados tardíos
o duplicados. `DiningReservationPolicyTest` comprueba las fechas de reserva,
los fines de semana y los cambios de mes y año.
`DiningPaymentTest` prueba la interfaz con autenticación y fecha inyectadas;
no sustituye la comprobación de la biometría en un dispositivo real.
