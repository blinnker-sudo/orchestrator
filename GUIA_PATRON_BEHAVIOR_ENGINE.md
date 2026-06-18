# Guía técnica: Behavior Engine (Strategy + Registry + Pipeline)

> Documento base para transferencia de conocimiento al equipo.
> Autor: Hiram Leal Hernández · Estado: en construcción

---

## 1. ¿Qué es esto y por qué existe?

El **Behavior Engine** es un motor que ejecuta "pasos de negocio" (behaviors)
de forma configurable. La secuencia de pasos de cada flujo vive en
configuración (JSON), no en código.

### El problema que resuelve

Antes existía **una interfaz "gorda" compartida** que implementaban las
librerías de cada país (México, Perú). Cada vez que un país necesitaba un paso
nuevo:

1. Se agregaba el método a la interfaz compartida.
2. El país que lo necesitaba lo implementaba con lógica real.
3. El **otro país quedaba obligado a implementarlo** aunque no le aplicara
   → método vacío / stub / "not supported".

Consecuencias:
- **Acoplamiento entre países**: un cambio de México obligaba a tocar Perú.
- **Código muerto**: métodos vacíos solo para que compile.
- **Interfaz que crece sin control**: nadie sabía qué método aplicaba a quién.
- **Contrato mentiroso**: la firma dice que el método existe, pero no hace nada.

Esto viola el **Interface Segregation Principle** (SOLID): ningún cliente
debería estar forzado a depender de métodos que no usa.

### La solución

Se invierte el diseño: en vez de **una interfaz con N métodos** que todos
implementan, hay **N clases pequeñas** que implementan **una interfaz mínima**.
Cada país tiene solo los behaviors que le aplican. Agregar un paso = crear una
clase nueva en la lib correspondiente + declararla en JSON, **sin tocar la
maquinaria del motor ni los behaviors de otros países** (principio Open/Closed).

---

## 2. ¿Qué es "GoF"?

**GoF = "Gang of Four"** (La Banda de los Cuatro): los cuatro autores del libro
*Design Patterns: Elements of Reusable Object-Oriented Software* (1994) —
Gamma, Helm, Johnson y Vlissides. Ese libro catalogó **23 patrones de diseño**
clásicos de POO y es la referencia fundacional del tema.

De esos 23, este motor usa dos (Strategy y Chain of Responsibility) y los
combina con un tercer patrón posterior (Registry, popularizado por Martin
Fowler en *Patterns of Enterprise Application Architecture*; también conocido
como *Service Locator*).

---

## 3. Los tres patrones

### 3.1 Strategy — "acciones intercambiables tras una interfaz común"

Encapsula cada acción/algoritmo en su propia clase, todas con la misma
interfaz, para hacerlas intercambiables. Evita el `switch/if-else` gigante.

```ts
interface Behavior {
  name: string;
  execute(ctx): Promise<unknown>;
}
```

Cada `*.behavior.ts` (validate.rfc, compare.ocr, ...) es una *estrategia
concreta*. Quien las usa no sabe qué clase concreta es: habla con la interfaz.

### 3.2 Registry — "catálogo que resuelve por nombre"

Mantiene un `Map<string, Behavior>` para resolver la implementación correcta a
partir de una clave (un string que puede venir del JSON).

En este motor el registry se llena **solo, por descubrimiento**: recorre los
providers de NestJS y registra los que parecen behaviors (tienen `name` string
y `execute` función). No se escribe el registro a mano.

### 3.3 Pipeline (Chain of Responsibility) — "pasos en orden con estado"

Ejecuta una secuencia de behaviors en orden, compartiendo un contexto
acumulado (`ctx.state`). Cada paso puede leer lo que dejaron los anteriores.
Diferencia con el Chain of Responsibility puro: aquí **todos** los pasos se
ejecutan (no se corta la cadena).

### Tabla resumen

| Patrón   | Qué hace                                      | Dónde vive                         |
|----------|-----------------------------------------------|------------------------------------|
| Strategy | Acciones intercambiables tras interfaz común  | `*.behavior.ts` en las libs        |
| Registry | Catálogo nombre → instancia                   | `behavior-registry.service.ts`     |
| Pipeline | Ejecución secuencial con estado compartido    | `behavior-registry.service.ts`     |
| Config   | Qué pasos y en qué orden                       | `*.behaviors.json` en la API       |

### Analogía (cocina de restaurante)

- **Strategy** = cada cocinero sabe hacer un platillo; todos responden a la
  misma orden "prepara lo tuyo".
- **Registry** = el directorio del chef: "para 'postre' → cocinero X".
- **Pipeline** = la receta: "corta, cocina, emplata" en orden, cada paso usa lo
  que dejó el anterior.
- **JSON** = el menú: qué recetas hay y qué pasos lleva cada una, sin reescribir
  la cocina.

### Nota de precisión

- Strategy y Chain of Responsibility → patrones GoF "oficiales".
- Registry → patrón de Martin Fowler (no está en el libro GoF original).

Frase rigurosa: *"el motor combina dos patrones GoF (Strategy + Chain of
Responsibility) con un patrón de Fowler (Registry)."*

---

## 4. Anatomía de las interfaces

Dos interfaces forman el contrato del patrón Strategy en este motor:
`CountryBehavior` (el contrato que toda acción cumple) y
`CountryBehaviorContext` (la información con la que la acción trabaja).

### 4.1 CountryBehaviorContext — "la mochila que viaja por el pipeline"

```ts
export interface CountryBehaviorContext<TInput = DataWorkflowDto> {
  input: TInput;
  state: Record<string, unknown>;
  metadata: BehaviorMetadata;
}
```

Es el ÚNICO parámetro que recibe cada behavior en su `execute`. Piensa en él
como una mochila que se pasa de paso en paso. Tiene tres compartimentos:

- **`input`** — los datos de entrada (el `DataWorkflowDto`: flow, requestId,
  data...). Es el MISMO para todos los behaviors del pipeline. Conceptualmente
  de solo lectura: representa "lo que pidió el cliente"; nadie debe mutarlo.

- **`state`** — la memoria compartida y MUTABLE. Es un diccionario
  `{ [nombre]: resultado }`. Cada behavior, al terminar, deja su resultado:
  `ctx.state['validate.rfc'] = { rfcValido: true }`, y el siguiente puede
  leerlo. Es el HILO CONDUCTOR que conecta pasos independientes. Sin esto, cada
  behavior estaría aislado y no se podría encadenar lógica.

- **`metadata`** — información del proceso (no del negocio). Datos técnicos del
  request, como `requestId` para trazabilidad/correlación.

**El genérico `<TInput = DataWorkflowDto>`**: por defecto `input` es un
`DataWorkflowDto`, pero un behavior puede especializar qué tipo de input
espera. El `= DataWorkflowDto` es el valor por defecto del genérico: si no
especificas nada, asume ese tipo.

### 4.2 CountryBehavior — "el contrato que toda acción debe cumplir"

```ts
export interface CountryBehavior<TInput = DataWorkflowDto, TOutput = unknown> {
  readonly name: string;
  execute(ctx: CountryBehaviorContext<TInput>): Promise<TOutput>;
}
```

Es la interfaz Strategy. Solo DOS miembros (minimalismo a propósito: lo opuesto
a la interfaz "gorda" del problema original):

- **`readonly name: string`** — el identificador único. Es LA LLAVE del Registry:
  el `discover()` usa este valor para meter el behavior al `Map`, y el JSON lo
  referencia por este mismo string. Debe coincidir con el JSON. `readonly`
  significa que no se reasigna después de construido: el nombre es fijo.

- **`execute(ctx): Promise<TOutput>`** — el único método. Recibe la mochila
  (`ctx`), hace su trabajo y devuelve una promesa con su resultado. Es async por
  naturaleza (casi siempre llama a servicios, BD, APIs).

**Los genéricos `<TInput, TOutput>`**: `TInput` = qué tipo de input espera (se
propaga al ctx); `TOutput` = qué tipo de resultado devuelve. Ejemplo:

```ts
class CompareOcrBehavior
  implements CountryBehavior<DataWorkflowDto, CompareOcrResult> {
  //                         ↑ input          ↑ lo que devuelve
}
```

TypeScript obliga a que `execute` devuelva exactamente un `CompareOcrResult`.

### 4.3 Cómo encajan

```
CountryBehavior (el contrato)
  └─ execute(ctx: CountryBehaviorContext)   ← recibe la mochila
                    │
                    ├─ ctx.input     → datos del request (compartido)
                    ├─ ctx.state     → memoria que se acumula entre pasos
                    └─ ctx.metadata  → info técnica del proceso
```

`CountryBehavior` define QUÉ debe hacer una acción; `CountryBehaviorContext`
define CON QUÉ información la hace. Juntas son todo el contrato del patrón
Strategy en este motor.

---

## 5. Flujo de una request (todo junto)

`POST /workflows/complete` con `{ flow: "onboarding" }`:

```
1. Controller   → lee JSON: flows["onboarding"]["COMPLETE"]
                  → ["validate.rfc", "register.sat", "notify.email"]
2. runPipeline  → para cada nombre:
3. Registry     →   behaviors.get("validate.rfc") → la instancia
4. Strategy     →   behavior.execute(ctx) → corre esa lógica
5. Pipeline     →   guarda resultado en ctx.state, pasa al siguiente
6. Controller   → devuelve el último resultado
```

---

## 6. Componentes del motor (el "core" que NO se modifica)

- **BehaviorRegistry**: discovery + Map + `run` / `runPipeline`.
- **Dispatch del controller**: resuelve `flow + step` → lista de behaviors.
- **Runner de pipeline**: itera la lista y mantiene `ctx.state`.

Este core permanece cerrado a modificación. El sistema se extiende agregando
clases (behaviors), no editando la maquinaria.

---

## 7. Cómo agregar un behavior nuevo (paso a paso)

> Pendiente de completar con ejemplo de código real.

1. Crear la clase `MiNuevoBehavior` en la lib del país (`@Injectable`, con
   `readonly name = 'mi.nuevo'` y método `execute`).
2. Registrarla en el módulo del país (`providers`).
3. Declarar su `name` en el JSON del flujo correspondiente, en el step deseado.
4. Listo: el registry la descubre sola; el pipeline la ejecuta en su turno.

---

## 8. Decisiones de diseño y trade-offs

> Pendiente de completar. Temas a documentar:
> - Duck typing vs. decorador `@RegisterBehavior` (por qué se eliminó el decorador).
> - Contratos duplicados entre repos ("Option B") vs. librería de contratos compartida.
> - Manejo de errores: lanzar excepciones desde `execute` y dejarlas volar al filter.
> - BusinessException compartida: debe vivir en un solo lugar (identidad runtime / instanceof).

---

## 9. Glosario

- **CountryBehavior**: una clase que implementa un paso de negocio (`name` + `execute`).
- **Pipeline**: secuencia de behaviors que se ejecutan en orden.
- **Step**: etapa del flujo (INIT, LAYOUT, COMPLETE).
- **Flow**: un proceso de negocio (onboarding, n2, ...).
- **ctx.state**: contexto acumulado que comparten los behaviors de un pipeline.
- **Registry**: catálogo que resuelve un behavior por su nombre.
```
