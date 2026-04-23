# Guía de configuración de Kafka 4.2 (KRaft) en Windows

Setup completo de Kafka local sin ZooKeeper, sin Docker, en Windows.

## Pre-requisitos

- **Java 17 o superior** instalado y `JAVA_HOME` configurado en variables de entorno.
- Verifica con: `java -version`
- Kafka 4.2 binario descargado y extraído en una **ruta sin espacios**, por ejemplo `C:\software\kafka`.
    - Descarga: https://kafka.apache.org/downloads (binario `kafka_2.13-4.2.0.tgz`)

---

## 1. Configuración inicial de `server.properties`

Editar el archivo `C:\software\kafka\config\server.properties` y asegurar que tenga estas líneas (las demás se pueden dejar como vienen por defecto):

```properties
process.roles=broker,controller
node.id=1
controller.quorum.bootstrap.servers=localhost:9093
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://localhost:9092
controller.listener.names=CONTROLLER
inter.broker.listener.name=PLAINTEXT
log.dirs=C:/software/kafka/kafka-logs
```

### Notas importantes

- En `log.dirs` usar **forward slashes** (`/`), no backslashes. Kafka interpreta mal los `\` en Windows.
- `controller.quorum.bootstrap.servers` lleva solo `host:port`, **NO** lleva el prefijo `node_id@`.
- Si por alguna razón se agrega `controller.quorum.voters`, esa propiedad sí lleva `1@localhost:9093` (es la diferencia confusa entre ambas).

---

## 2. Inicialización (solo la primera vez)

Esto se hace **una sola vez**, cuando se instala Kafka desde cero o cuando se quiere reiniciar todo el storage. No se repite cada que se arranca el broker.

### Generar Cluster UUID

```cmd
cd C:\software\kafka
bin\windows\kafka-storage.bat random-uuid
```

Copiar el UUID que imprime (algo como `BsMfHqRQRnqwOS09CXpayw`).

**Si `random-uuid` falla**, usar este workaround en PowerShell:

```powershell
[guid]::NewGuid().ToString()
```

### Formatear el storage

```cmd
bin\windows\kafka-storage.bat format --standalone -t PEGAR_AQUI_EL_UUID -c config\server.properties
```

El flag `--standalone` es nuevo en Kafka 4.x y simplifica el setup single-node.

---

## 3. Arrancar el broker

**Esto sí se hace cada vez que se quiere usar Kafka.**

```cmd
cd C:\software\kafka
bin\windows\kafka-server-start.bat config\server.properties
```

Dejar esa ventana **abierta y corriendo**. Esperar a que aparezca un mensaje tipo `Kafka Server started`. Si se cierra la ventana, Kafka se detiene.

Para detener Kafka limpiamente: `Ctrl+C` en esa ventana.

---

## 4. Crear los topics

**Importante:** el broker tiene que estar corriendo (paso 3). Estos comandos se ejecutan en una **terminal NUEVA**, no en la del broker.

```cmd
cd C:\software\kafka

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-start-orchestrator-request-country --replication-factor 1 --partitions 4

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-resume-orchestrator-request-country --replication-factor 1 --partitions 4

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-delivery-orchestrator-request-country --replication-factor 1 --partitions 4

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-init-orchestrator-request-country --replication-factor 1 --partitions 4

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-start-orchestrator-response-country --replication-factor 1 --partitions 2

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-resume-orchestrator-response-country --replication-factor 1 --partitions 2

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-delivery-orchestrator-response-country --replication-factor 1 --partitions 3

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-init-orchestrator-response-country --replication-factor 1 --partitions 3

bin\windows\kafka-topics.bat --create --if-not-exists --bootstrap-server localhost:9092 --topic dev-ib-platform-event-tracing-country --replication-factor 1 --partitions 5
```

Los topics persisten en el storage formateado, así que no se tienen que recrear cada que se arranca el broker.

---

## 5. Comandos de gestión de topics

### Listar todos los topics

```cmd
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

### Describir un topic (ver particiones, replicación, configuración)

```cmd
bin\windows\kafka-topics.bat --describe --topic NOMBRE_TOPIC --bootstrap-server localhost:9092
```

### Borrar un topic

```cmd
bin\windows\kafka-topics.bat --delete --topic NOMBRE_TOPIC --bootstrap-server localhost:9092
```

El borrado es asíncrono, puede tardar unos segundos en desaparecer del `--list`.

---

## 6. Prueba de producer y consumer desde terminal

Útil para verificar que Kafka funciona sin involucrar la app Nest.

### Terminal A — Consumer (escucha)

```cmd
cd C:\software\kafka
bin\windows\kafka-console-consumer.bat --topic dev-ib-platform-start-orchestrator-request-country --from-beginning --bootstrap-server localhost:9092
```

Se queda esperando mensajes. No muestra prompt, eso es normal.

### Terminal B — Producer (envía)

```cmd
cd C:\software\kafka
bin\windows\kafka-console-producer.bat --topic dev-ib-platform-start-orchestrator-request-country --bootstrap-server localhost:9092
```

Aparece prompt `>`. Escribir mensajes y presionar Enter:

```
> hola test
> {"key":"123","flow":"N2_ONBOARDING"}
```

Cada mensaje debería aparecer instantáneamente en la Terminal A.

Para cerrar: `Ctrl+C` en cada terminal.

---

## 7. Flujo diario (después de la configuración inicial)

Una vez configurado todo, el flujo de cada día es solo:

1. Abrir CMD y arrancar el broker:
   ```cmd
   cd C:\software\kafka
   bin\windows\kafka-server-start.bat config\server.properties
   ```
2. Levantar la app Nest apuntando a `localhost:9092`.

Los topics ya existen y persisten en `C:/software/kafka/kafka-logs`.

---

## 8. Resetear todo desde cero

Si algo se corrompe o se quiere empezar de cero:

1. Detener el broker (`Ctrl+C` en su ventana).
2. Borrar la carpeta `C:\software\kafka\kafka-logs`.
3. Repetir desde el **paso 2** (generar nuevo UUID y formatear).
4. Volver a crear los topics (paso 4).

---

## Troubleshooting común

### `Timed out waiting for a node assignment`

El broker no está corriendo, o el cliente no puede conectarse. Verificar:
- Que la ventana del broker siga abierta y sin errores.
- Que el puerto 9092 esté escuchando: `netstat -ano | findstr :9092`
- Que no haya firewall/antivirus corporativo bloqueando.

### `UNKNOWN_TOPIC_OR_PARTITION`

El topic no existe. Crearlo manualmente con el comando del paso 4.

### `Failed to parse host name from 1@localhost:9093`

La propiedad `controller.quorum.bootstrap.servers` tiene formato incorrecto. Debe ser solo `localhost:9093`, sin el `1@`.

### Mensaje "Reconfiguration failed: No configuration found for '5e2de80c'"

Es ruido de Log4j al arrancar comandos `kafka-*.bat`. Es ignorable, no afecta la funcionalidad.

### Errores con `random-uuid` en CMD

Usar el workaround de PowerShell: `[guid]::NewGuid().ToString()`