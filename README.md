# Finmail

**Demo de la app:** [finmail.toval.dev](https://finmail.toval.dev) · Frontend: [finmail-pwa](https://github.com/tovalh/finmail-pwa)

Backend para una app de finanzas personales que lee los correos de notificación del banco, parsea las transacciones automáticamente y las categoriza con IA.

Nació porque los bancos chilenos no tienen APIs abiertas para uso personal. Mientras eso no cambie, esto funciona leyendo los correos que el banco ya envía.

Por ahora soporta Banco de Chile. Para sumar otro banco basta con escribir su parser de correos; auth, categorías, IA y endpoints no cambian.

## Como funciona

Se conecta a Gmail via OAuth2 y revisa cada 5 minutos si llegaron correos nuevos del banco. Cuando encuentra uno, parsea el monto, comercio y fecha, guarda la transacción y le pide a Claude una categoría. El usuario confirma o cambia la categoría desde la [PWA](https://github.com/tovalh/finmail-pwa). Una vez que un comercio tiene categoría asignada, se aplica automáticamente a las siguientes transacciones del mismo comercio y ya no se consulta al modelo.

La respuesta de Claude se pide con un JSON schema cuyo `enum` son tus categorías, así que el modelo solo puede devolver una que exista. La carga histórica clasifica los comercios nuevos en lotes de 50 al final, en vez de una llamada por correo.

## Stack

- Java 25, Spring Boot 4
- PostgreSQL
- Gmail API
- API de Claude (Haiku 5.5, structured outputs)
- JWT

## Configuración

**1. Crear la base de datos**

```sql
CREATE DATABASE finmail;
```

**2. Configurar la app**

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Completar los valores en el archivo.

**3. Gmail API**

- Crear un proyecto en Google Cloud Console y habilitar Gmail API
- Crear credenciales OAuth 2.0 (Desktop app)
- Descargar `credentials.json` y colocarlo en `src/main/resources/`

La primera vez que corre la app se abre el navegador para autorizar el acceso. El token queda guardado en `tokens/`.

**4. API key de Claude**

Crearla en la [consola de Claude](https://platform.claude.com/settings/keys) y dejarla en la variable de entorno `ANTHROPIC_API_KEY` (o directo en `app.claude.api-key`). El modelo se cambia con `app.claude.model`; por defecto usa `claude-haiku-5-5`, que para clasificar sobra.

**5. Correr**

```bash
./mvnw spring-boot:run
```

## Agregar otro banco

Cada banco es una clase en el paquete `parser` que implementa `ParserCorreoBanco`:

```java
@Component
public class OtroBancoParser implements ParserCorreoBanco {
    public List<String> remitentes() { return List.of("avisos@otrobanco.cl"); }
    public Optional<MovimientoBancario> parsear(CorreoBanco correo) { ... }
}
```

Spring inyecta todos los parsers en `TransaccionService` y `GmailService`, así que con crear la clase el banco ya se consulta en Gmail y se procesa. `BancoChileParser` sirve de ejemplo y tiene sus tests en `BancoChileParserTest`.

## Endpoints

Todos los endpoints requieren `Authorization: Bearer <token>` excepto `/api/auth/**`.

**Auth**

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| POST | /api/auth/login | Obtener access y refresh token |
| POST | /api/auth/refresh | Renovar access token |

**Transacciones**

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| GET | /api/transacciones?desde=YYYY-MM-DD&hasta=YYYY-MM-DD | Listar transacciones por rango de fecha |
| GET | /api/transacciones/resumen?desde=YYYY-MM-DD&hasta=YYYY-MM-DD | Resumen de gastos por categoria |
| PATCH | /api/transacciones/{id}/categoria | Asignar categoria a una transaccion |
| POST | /api/transacciones/historico?desde=YYYY-MM-DD | Importar historico desde Gmail |

**Categorias**

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| GET | /api/categorias | Listar categorias |
| POST | /api/categorias | Crear categoria |
| PATCH | /api/categorias/{id} | Editar nombre, color o icono |
| DELETE | /api/categorias/{id} | Eliminar categoria (solo si no tiene transacciones) |

**Configuracion**

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| GET | /api/config | Obtener configuracion |
| PATCH | /api/config | Actualizar configuracion |