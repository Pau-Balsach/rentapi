# 🏠 RentAPI

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Supabase](https://img.shields.io/badge/Supabase-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Render](https://img.shields.io/badge/Render-46E3B7?style=for-the-badge&logo=render&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)

**API REST pública y open source de estadísticas del mercado de alquiler en España.**

RentAPI agrega datos de portales inmobiliarios (Idealista, Habitaclia) y los transforma en estadísticas limpias y estructuradas: precios medios, tendencias mensuales, rankings por ciudad y barrio, y evaluación de pisos concretos.

> 🌐 **API Live:** https://rentapi-tuaq.onrender.com
> 📖 **Swagger UI:** https://rentapi-tuaq.onrender.com/swagger-ui/index.html

---

## ¿Qué problema resuelve?

Los portales inmobiliarios muestran pisos individuales pero no ofrecen:
- API pública gratuita con estadísticas de mercado
- Historial de precios accesible por zona
- Agregación por barrio con percentiles y tendencias

RentAPI cubre ese hueco. No devuelve listados de pisos — devuelve **inteligencia de mercado**.

---

## Casos de uso

- Un periodista compara el precio medio del alquiler en Barcelona vs Madrid en los últimos 6 meses
- Un desarrollador construye un comparador de ciudades para decidir dónde vivir
- Un ciudadano evalúa si el piso que le ofrecen está por encima o por debajo del mercado de su barrio

---

## Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Backend | Java 17 + Spring Boot 3 |
| Base de datos | PostgreSQL via Supabase |
| ORM | Spring Data JPA + Hibernate |
| Scraping | Jsoup |
| Autenticación | API Key propia (header `X-API-Key`) |
| Rate limiting | Bucket4j + tabla `api_usage_log` |
| Documentación | Swagger UI via springdoc-openapi |
| Testing | JUnit 5 + Mockito + JaCoCo |
| CI/CD | GitHub Actions |
| Deploy | Render.com (Docker) |

---

## Primeros pasos

### 1. Obtener una API Key

```bash
curl -X POST https://rentapi-b6gc.onrender.com/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "tu@email.com", "name": "Tu Nombre"}'
```

Respuesta:
```json
{
  "api_key": "a1b2c3d4e5f6...",
  "plan": "free",
  "requests_per_day": 100
}
```

### 2. Usar la API

Incluye tu API Key en el header `X-API-Key` en todas las peticiones autenticadas:

```bash
curl https://rentapi-b6gc.onrender.com/api/v1/stats/ciudad/barcelona \
  -H "X-API-Key: tu_api_key"
```

---

## Endpoints

### Auth

| Método | Endpoint | Auth | Descripción |
|--------|----------|------|-------------|
| `POST` | `/api/v1/auth/register` | No | Registra un usuario y genera API Key |
| `GET` | `/api/v1/auth/me` | Sí | Info del usuario y uso actual |

### Geografía

| Método | Endpoint | Auth | Descripción |
|--------|----------|------|-------------|
| `GET` | `/api/v1/geo/comunidades` | No | Lista todas las comunidades autónomas |
| `GET` | `/api/v1/geo/provincias` | No | Lista provincias (filtrable por comunidad) |
| `GET` | `/api/v1/geo/ciudades` | No | Lista ciudades disponibles (filtrable por provincia) |
| `GET` | `/api/v1/geo/barrios` | No | Lista barrios de una ciudad |

### Estadísticas

| Método | Endpoint | Auth | Descripción |
|--------|----------|------|-------------|
| `GET` | `/api/v1/stats/ciudad/{slug}` | Sí | Estadísticas agregadas de una ciudad |
| `GET` | `/api/v1/stats/barrio/{ciudad}/{barrio}` | Sí | Estadísticas de un barrio concreto |
| `GET` | `/api/v1/stats/comparar` | Sí | Compara hasta 5 ciudades o barrios |
| `GET` | `/api/v1/stats/tendencia/{slug}` | Sí | Evolución histórica mensual de precios |
| `GET` | `/api/v1/stats/ranking` | Sí | Ranking de zonas por precio |
| `GET` | `/api/v1/stats/evaluar` | Sí | Evalúa si un precio está en mercado |

### Sistema

| Método | Endpoint | Auth | Descripción |
|--------|----------|------|-------------|
| `GET` | `/api/v1/health` | No | Estado del sistema |
| `GET` | `/api/v1/meta/cobertura` | No | Cobertura de datos y última actualización |

---

## Ejemplos

### Estadísticas de una ciudad

```bash
curl "https://rentapi-b6gc.onrender.com/api/v1/stats/ciudad/barcelona?habitaciones=2" \
  -H "X-API-Key: tu_api_key"
```

```json
{
  "ciudad": "Barcelona",
  "periodo": { "desde": "2025-11-01", "hasta": "2026-05-01" },
  "total_pisos_analizados": 4821,
  "precio_mes": {
    "media": 1423.50,
    "mediana": 1280.00,
    "min": 650.00,
    "max": 6500.00,
    "percentil_25": 950.00,
    "percentil_75": 1800.00
  },
  "precio_m2": {
    "media": 17.30,
    "mediana": 16.10
  }
}
```

### Comparar ciudades

```bash
curl "https://rentapi-b6gc.onrender.com/api/v1/stats/comparar?zonas=barcelona,madrid,valencia&tipo=ciudad" \
  -H "X-API-Key: tu_api_key"
```

```json
{
  "comparativa": [
    { "zona": "Barcelona", "precio_medio_m2": 17.30, "precio_medio_mes": 1423 },
    { "zona": "Madrid",    "precio_medio_m2": 15.80, "precio_medio_mes": 1310 },
    { "zona": "Valencia",  "precio_medio_m2": 10.20, "precio_medio_mes": 890  }
  ],
  "zona_mas_cara": "Barcelona",
  "zona_mas_barata": "Valencia"
}
```

### Evaluar un piso

```bash
curl "https://rentapi-b6gc.onrender.com/api/v1/stats/evaluar?ciudad=barcelona&barrio=gracia&precio=1800&habitaciones=2" \
  -H "X-API-Key: tu_api_key"
```

```json
{
  "precio_consultado": 1800,
  "precio_medio_zona": 1530,
  "valoracion": "por_encima",
  "diferencia_porcentaje": "+17.6%",
  "percentil_zona": 76,
  "recomendacion": "Este precio está un 17% por encima de la media del barrio para pisos de 2 habitaciones en Gràcia."
}
```

---

## Rate limiting

| Plan | Peticiones/día |
|------|---------------|
| Free | 100 |
| Pro | 1000 |

Cuando se supera el límite, la API devuelve `429 Too Many Requests`.

---

## Ejecutar en local

### Requisitos
- Docker y Docker Compose
- Cuenta en Supabase (o PostgreSQL local)

### Pasos

```bash
# 1. Clona el repositorio
git clone https://github.com/Pau-Balsach/rentapi.git
cd rentapi

# 2. Crea el archivo de configuración
cp src/main/resources/application.properties.example src/main/resources/application.properties
# Edita application.properties con tus credenciales de Supabase

# 3. Levanta con Docker Compose
docker-compose up --build

# 4. La API estará disponible en http://localhost:8080
# Swagger UI en http://localhost:8080/swagger-ui/index.html
```

### Variables de entorno necesarias

| Variable | Descripción |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | URL JDBC de tu base de datos PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la base de datos |
| `SPRING_DATASOURCE_PASSWORD` | Password de la base de datos |

---

## Tests

```bash
# Ejecutar todos los tests
mvn test

# Ver reporte de cobertura (JaCoCo)
mvn verify
# El reporte se genera en target/site/jacoco/index.html
```

---

## Nota legal

El scraping de portales como Idealista o Habitaclia puede estar restringido en sus Términos de Servicio. Este proyecto es de carácter **educativo y de portfolio**. El scraping se realiza de forma respetuosa: delays entre peticiones, sin sobrecargar servidores, respetando `robots.txt` en la medida de lo posible. El autor asume la responsabilidad del uso.

---

## Autor

**Pau Balsach** — [Portfolio](https://paubalsach-portfolio.netlify.app)

---

## Licencia

[MIT](LICENSE)
