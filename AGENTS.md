# Engineering Guidelines

- Do not add or run tests unless the user explicitly requests them.
- Keep controllers thin: define routes, validate request DTOs, and return response DTOs.
- Put business rules in a service interface and its `serviceImpl` implementation.
- Use `@RestControllerAdvice` for API exception responses. Reuse an existing handled exception when it fits before adding a new exception type.
- Avoid unnecessary database queries. Prefer existing relationships, database constraints, and one well-scoped query over repeated existence checks.
- Keep code direct, readable, and naturally written. Avoid generated-looking abstractions and excessive comments.
- Do not commit environment files or credentials.
