## Things to Do

Because the key pair is generated in memory at startup, all existing JWTs become invalid when the application restarts. Users would need to re-authenticate. For a production system, you'd typically load the key pair from a file, environment variable, or secret manager so it persists across restarts. For a starter template, in-memory generation keeps things simple with no external dependencies.

UUID vs. Long for Repository classes?

## Commands

docker exec -it mysql mysql -u root -p