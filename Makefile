
# Define make help functionality
.DEFAULT_GOAL := help
define PRINT_HELP_PYSCRIPT
import re, sys
for line in sys.stdin:
	match = re.match(r'^([a-zA-Z_-]+):.*?## (.*)$$', line)
	if match:
		target, help = match.groups()
		print("%-30s %s" % (target, help))
endef
export PRINT_HELP_PYSCRIPT

help: ## Get list of make commands (default target)
	@printf -- "Make commands for lakehouse-poc\n\n"
	@python -c "$$PRINT_HELP_PYSCRIPT" < $(MAKEFILE_LIST) | sort

down: ## stops and removes all containers
	docker compose down

stop: ## stops all containers
	docker compose stop

up: ## starts all containers
	docker compose up -d

start-postgres-connector: ## registers and starts postgres connector
	curl -i -X POST -H "Accept:application/json" -H  "Content-Type:application/json" http://localhost:8083/connectors/ -d @connect/register-postgres-sr.json

consume-messages: ## consume messages from schema registry
	docker-compose exec schema-registry /usr/bin/kafka-avro-console-consumer \
		--bootstrap-server kafka:9092 \
		--from-beginning \
		--property print.key=true \
		--property schema.registry.url=http://schema-registry:8081 \
		--topic dbserver1.inventory.customers

login-postgres: ## access psql client for postgres service
	docker compose exec postgres env PGOPTIONS="--search_path=inventory" bash -c 'psql -U $(POSTGRES_USER) postgres'

scala-build: ## build scala image
	docker compose -f docker-compose.dev.yaml build scala

scala-dev: ## access scala dev env
	docker compose -f docker-compose.dev.yaml run --rm scala

spark-build: ## build spark image
	docker compose build spark

restart-spark: ##restart spark service with new build
	docker compose stop spark && \
	docker compose build spark && \
	docker compose up -d spark

delta-bronze: ## start bronze layer process
	docker compose exec spark /bin/bash -c 'spark-submit $$SPARK_SUBMIT_BRONZE'

delta-silver: ## start silver layer process
	docker compose exec spark /bin/bash -c 'spark-submit $$SPARK_SUBMIT_SILVER'

spark-shell: ## access spark-shell env
	docker compose exec spark spark-shell

clean-s3: ## remove s3 data volume
	docker volume rm lakehouse-poc_data

clean: ## Removes all volumes
	docker compose down -v