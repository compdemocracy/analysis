# No default target
.PHONY: help

help:
	@echo "Available targets:"
	@echo " build - Build the Docker image"
	@echo " run - Run the Docker container"
	@echo " kill - Kill the running Docker container"

build:
	docker build -t polis-analysis:local .

run:
	docker run --rm -p 3850:3850 -p 3860:3860 -p 3870:3870 -v $(pwd):/usr/src/app polis-analysis:local

kill:
	docker kill $$(docker ps -q --filter ancestor=polis-analysis:local)
