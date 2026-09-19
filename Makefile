.PHONY: all test fmt clippy android ios release clean

all: test

# ── Rust ──────────────────────────────────────────────────────────────

test:
	cargo test --workspace

fmt:
	cargo fmt --all

clippy:
	cargo clippy --workspace -- -D warnings

# ── 构建 ─────────────────────────────────────────────────────────────

android:
	./scripts/build-android.sh

ios:
	./scripts/build-ios.sh

# ── 发布 ─────────────────────────────────────────────────────────────

release:
	@if [ -z "$(VERSION)" ]; then \
		echo "Usage: make release VERSION=v0.2.0-alpha.1"; \
		exit 1; \
	fi
	./scripts/release.sh $(VERSION)

# ── 清理 ─────────────────────────────────────────────────────────────

clean:
	cargo clean
	rm -rf android/app/src/main/jniLibs/
	rm -rf android/app/src/main/java/uniffi/
	rm -rf ios/CameraApp/Generated/
	@echo "✅ Cleaned"