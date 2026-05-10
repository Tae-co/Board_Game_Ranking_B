# Board_Game_Ranking

## Local Development

### Prerequisites
- Java 21
- Supabase CLI with local stack running (`supabase start`)

### Default local database
The app and Flyway are configured to use your local Supabase DB by default:
- URL: `jdbc:postgresql://127.0.0.1:54322/postgres`
- Username: `postgres`
- Password: `postgres`

Supabase local API URL:
- `http://127.0.0.1:54321`

You can override datasource settings with:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Optional environment variables
- `JWT_SECRET` (defaults to a local dev secret if omitted)
- `SUPABASE_SERVICE_ROLE_KEY` (required for Supabase storage features)
- `SUPABASE_URL` (defaults to `http://127.0.0.1:54321`)
- `FRONTEND_URL` and `BACKEND_URL` (used for OAuth callback flow)

### Run database migrations (Gradle)
```bash
./gradlew flywayMigrate
```

### Run the application locally
1. Create local env file from template:
```bash
cp .env.local.example .env.local
```
2. Update `.env.local` with the values you need for local work.
3. Set up local Supabase by following the official tutorial:
- https://supabase.com/docs/guides/local-development
4. Start local Supabase:
```bash
supabase start
```
5. Run database migrations:
```bash
./gradlew flywayMigrate
```
6. Run the app:
```bash
./run-local.sh
```
