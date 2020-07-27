# Makefile adapted from
# from https://tech.davis-hansson.com/p/make/

# Use bash as the shell
SHELL := bash
# Exit on any shell error
.SHELLFLAGS := -eu -o pipefail -c
# Delete target file if script fails
.DELETE_ON_ERROR:
# Warn when a Make variable is not defined
MAKEFLAGS += --warn-undefined-variables
# Do not use standard rules for C builds
MAKEFLAGS += --no-builtin-rules

# No default target
.PHONY: all
all:


# Working tree state:
ALLOW_DIRTY=false

.PHONY: dirty
dirty:
	$(eval ALLOW_DIRTY=true)
	@echo "WARNING: Deploys will be allowed from a dirty working tree."


# Make sure there aren't uncommitted changes
.PHONY: check-clean-tree
check-clean-tree:
	@if [[ "$(ALLOW_DIRTY)" != "true" && -n "$$(git status --porcelain)" ]]; then \
		echo "ERROR: Working directory not clean."; \
	  exit 97; \
	fi


.PHONY: build
build:
	docker build . -f Dockerfile -t polis-analysis-docker


.PHONY: clean
clean:
ifeq ($(strip $(shell docker ps -aq)),)
	echo "No running processes"
else
	docker rm -fv $(shell docker ps -aq)
endif

nrepl_port ?=3850
oz_port ?=3860
jupyter_port ?=3870

.PHONY: run
run: clean
	docker run -p $(nrepl_port):3850 -p $(oz_port):3860 -p $(jupyter_port):3870 -v ${PWD}:/app -e CHOKIDAR_USEPOLLING=true polis-analysis-docker

