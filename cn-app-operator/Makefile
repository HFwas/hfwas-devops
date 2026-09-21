# cn-app-operator Makefile

# Image URL for deployment
IMG ?= hfwas/cn-app-operator:latest

# ENVTEST_K8S_VERSION refers to the version of kubebuilder assets to be downloaded by envtest binary.
ENVTEST_K8S_VERSION = 1.31.0

# Get the currently used golang install path (in GOPATH/bin, unless GOBIN is set)
ifeq (,$(shell go env GOBIN))
GOBIN=$(shell go env GOPATH)/bin
else
GOBIN=$(shell go env GOBIN)
endif

# Setting SHELL to bash allows bash-specific string handling
SHELL = /usr/bin/env bash -o pipefail
.SHELLFLAGS = -ec

.PHONY: all
all: build

##@ General

.PHONY: help
help:
	@echo "Usage:"
	@echo "  make build        - Build the operator binary"
	@echo "  make run          - Run operator locally"
	@echo "  make docker-build - Build docker image"
	@echo "  make deploy       - Deploy CRDs and operator to cluster"
	@echo "  make install      - Install CRDs to cluster"
	@echo "  make uninstall    - Uninstall CRDs from cluster"
	@echo "  make test         - Run tests"
	@echo "  make manifests    - Generate manifests (CRD yaml, RBAC)"

##@ Build

.PHONY: build
build:
	go build -o bin/operator cmd/operator/main.go

.PHONY: run
run:
	go run cmd/operator/main.go

.PHONY: docker-build
docker-build:
	docker build -t ${IMG} .

.PHONY: docker-push
docker-push:
	docker push ${IMG}

##@ Deployment

.PHONY: install
install:
	kubectl apply -f config/crd/

.PHONY: uninstall
uninstall:
	kubectl delete -f config/crd/

.PHONY: deploy
deploy: install
	kubectl apply -f config/rbac/
	kubectl apply -f config/manager/

.PHONY: undeploy
undeploy:
	kubectl delete -f config/manager/
	kubectl delete -f config/rbac/
	kubectl delete -f config/crd/

##@ Sample

.PHONY: sample-redis
sample-redis:
	kubectl apply -f config/samples/cloudcomponent-redis.yaml

.PHONY: sample-order-platform
sample-order-platform:
	kubectl apply -f config/samples/cloudcomponent-redis.yaml
	kubectl apply -f config/samples/cloudservice-order-core.yaml
	kubectl apply -f config/samples/app-order-platform.yaml

.PHONY: clean-samples
clean-samples:
	kubectl delete -f config/samples/ --ignore-not-found=true

##@ Development

.PHONY: manifests
manifests:
	controller-gen rbac:roleName=manager-role crd:maxDescLen=0 webhook paths="./..." output:crd:artifacts:config=config/crd/

.PHONY: fmt
fmt:
	go fmt ./...

.PHONY: vet
vet:
	go vet ./...

.PHONY: test
test:
	go test ./... -coverprofile cover.out

##@ Linting

.PHONY: lint
lint:
	golangci-lint run ./...