## Things to Do

Because the key pair is generated in memory at startup, all existing JWTs become invalid when the application restarts. Users would need to re-authenticate. For a production system, you'd typically load the key pair from a file, environment variable, or secret manager so it persists across restarts. For a starter template, in-memory generation keeps things simple with no external dependencies.

UUID vs. Long for Repository classes?

## Running Locally

There are two options.

### 1. IntelliJ + Docker Desktop MySQL

Start the MySQL container in Docker Desktop.

Then run the application from IntelliJ via the `TrailheadApplication` run configuration. The app uses the `dev` profile by default and connects to MySQL at `localhost:3306`.

Be sure to check that run config for environment variable values.

### 2. Full Docker Compose

Build and run the app inside a container alongside MySQL. This is how it will run on the production server.

```
docker compose --profile prod up -d --build    # Start app + MySQL
docker compose --profile prod logs -f app      # Tail app logs
docker compose --profile prod down             # Stop everything
docker compose --profile prod down -v          # Stop and wipe MySQL data
```

When the `prod` profile is active, the `mysql` service starts and the app connects to it over the internal Docker network.

### Environment Variables

Compose reads variables from a `.env` file in the project root. Required for the `prod` profile:

```
DB_PASSWORD=...
ADMIN_SEED_EMAIL=admin@yourdomain.com
ADMIN_SEED_PASSWORD=...
FRONTEND_BASE_URL=https://yourdomain.com
CORS_ALLOWED_ORIGINS=https://yourdomain.com
MAIL_HOST=smtp.yourprovider.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
```

For local IntelliJ runs, the same values can be set in the run configuration's Environment Variables field instead.

## Various Commands

Connect to your local MySQL container.

```
docker exec -it mysql mysql -u root -p
```
