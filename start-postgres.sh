#!/bin/bash

echo "Starting PostgreSQL Docker container..."
docker-compose up -d

echo "PostgreSQL is now running!"
echo "Connection details:"
echo "  Host: localhost"
echo "  Port: 5432"
echo "  Database: stockerdb"
echo "  Username: postgres"
echo "  Password: postgres" 