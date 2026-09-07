# yarch

> **Y**uan's **Arch**itecture — one contract, many dialects. · [中文文档（完整）](README.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![java-stack](https://github.com/ydonghao/yarch/actions/workflows/java-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/java-stack.yml)
[![python-stack](https://github.com/ydonghao/yarch/actions/workflows/python-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/python-stack.yml)
[![web-stack](https://github.com/ydonghao/yarch/actions/workflows/web-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/web-stack.yml)
[![golang-stack](https://github.com/ydonghao/yarch/actions/workflows/golang-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/golang-stack.yml)

A cross-project **engineering architecture platform**: not another CRUD framework, but a contract system that makes multiple tech stacks speak the same API language. Full documentation is in Chinese; this file is the English orientation.

## The idea

| Pain | yarch's answer |
|---|---|
| Every API looks different | One response envelope (`RestResponse`) + a global error-code registry — the same `code` means the same thing from any stack |
| Cross-stack debugging never lines up | ndjson logging + end-to-end traceId (W3C traceparent) across Java / Go / Python / TS |
| Conventions rely on self-discipline | Contract assertion tests + ArchUnit / depcruise / import-linter guards — violations fail CI |
| Scaffold forks rot | Platform components are versioned releases; upgrading is a one-line version bump |
| AI-generated code drifts | Templates own the structure, contracts own the behavior |

The contract layer (`contract/`, 25 normative specs, v1.0) is the single source of truth; each stack under `stacks/` implements it as idiomatic libraries. **Implementation diverging from contract is a bug** — both sides carry anti-drift assertions.

## One command from zero to a service

```bash
# Java (published to Maven Central)
mvn archetype:generate -B \
  -DarchetypeGroupId=io.github.ydonghao \
  -DarchetypeArtifactId=yarch-archetype-ddd \
  -DarchetypeVersion=0.1.0 \
  -DgroupId=com.example -DartifactId=my-svc -Dpackage=com.example.mysvc

# Web (published to npm — Semi / antd / arco UI presets)
npm create @yarch/admin@latest my-console

# Golang (from this repo until the tag is pushed)
go run ./cmd/yarch-init -module github.com/you/order-svc -out ~/code/order-svc

# Python (from this repo until the tag is pushed)
uv run yarch-init --service order-svc --out ~/code/order-svc
```

Generated projects are compliant by construction: unified envelope, 13-code error table, traceId propagation, pagination (page-number + keyset cursor), idempotency, soft delete, and CI guards pre-wired.

## Repository layout

```
contract/   25 normative specs (v1.0) — API, infra (PG/Redis/Kafka/...), web, registry
stacks/     java (16 modules) · python (uv workspace) · web (5 packages) · golang (3 modules)
clients/    mobile · miniprogram · desktop — grows on demand
docs/       architecture + comparative analyses (ruoyi / yudao / coli gap digests, in Chinese)
```

## Status

- Contract: 25 specs finalized (v1.0)
- Java: 16 modules, reactor verify green, CI on JDK 21/25 — 0.1.0 on Maven Central
- Web: 5 packages — 0.1.0 on npm
- Python: 103 tests green, CI on 3.12/3.13 (PyPI publishing pipeline ready, tag pending)
- Golang: 3 modules green (tag-based release path ready, tag pending)

## Contributing & License

See [CONTRIBUTING.md](CONTRIBUTING.md) (Chinese) and [SECURITY.md](SECURITY.md). Licensed under [Apache-2.0](LICENSE).
