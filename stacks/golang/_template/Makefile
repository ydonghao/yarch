run:
	go run ./cmd/server

build:
	CGO_ENABLED=0 go build -o bin/server ./cmd/server

test:
	go test ./...

lint:
	golangci-lint run

docker-up:
	docker compose up -d

.PHONY: run build test lint docker-up
