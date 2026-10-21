FROM flyway/flyway:11

# Build from the repository root:
#   docker build -f deploy/docker/db-migrate/Dockerfile -t hfwas/devops-db-migrate:latest .
COPY backend/db-migration/src/main/resources/db/migration /flyway/sql
