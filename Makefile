
down:
	docker compose down

stop:
	docker compose stop

up:
	docker compose up -d

start-postgres-connector:
	curl -i -X POST -H "Accept:application/json" -H  "Content-Type:application/json" http://localhost:8083/connectors/ -d @register-postgres-sr.json

consume-messages:
	docker-compose exec schema-registry /usr/bin/kafka-avro-console-consumer \
		--bootstrap-server kafka:9092 \
		--from-beginning \
		--property print.key=true \
		--property schema.registry.url=http://schema-registry:8081 \
		--topic dbserver1.inventory.customers

login-postgres:
	docker compose exec postgres env PGOPTIONS="--search_path=inventory" bash -c 'psql -U $(POSTGRES_USER) postgres'

scala-build:
	docker compose -f docker-compose.dev.yaml build scala

scala-dev:
	docker compose -f docker-compose.dev.yaml run --rm scala

restart-spark:
	docker compose stop spark && \
	docker compose build spark && \
	docker compose up -d spark

bronze:
	docker compose exec spark /bin/bash -c 'spark-submit $$SPARK_SUBMIT_BRONZE'

silver:
	docker compose exec spark /bin/bash -c 'spark-submit $$SPARK_SUBMIT_SILVER'

spark-shell:
	docker compose exec spark spark-shell

clean-s3:
	docker volume rm lakehouse-poc_data

# Removes all volumes
clean:
	docker compose down -v