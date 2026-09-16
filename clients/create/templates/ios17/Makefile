.PHONY: generate build test lint format

generate:
	xcodegen generate

build: generate
	set -o pipefail; xcodebuild -project {{appClassName}}.xcodeproj -scheme {{appClassName}} -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build

test:
	cd Packages/Features/Login && swift test
	cd Packages/Features/Users && swift test

lint:
	swiftlint lint --strict
	swiftformat --lint .

format:
	swiftformat .
