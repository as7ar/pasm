# PASM

Rust로 작성한 WASM 플러그인을 PaperMC에서 실행하는 프레임워크.

```
Rust 플러그인 --(proc macro)--> WASM --> PASM Host(Paper) --> Chicory --> Paper API
```

## 현재 상태 (Phase 1)

동작: `#[plugin]` → WASM → PASM Host → Chicory 로딩 → 메타데이터 발견 → `on_enabled`/`on_disabled` → `logger::info/warn/error`.

검증 범위:
- Rust: `cargo test --workspace` (macro 단위 테스트, 플러그인 lifecycle 통합 테스트) 통과, `wasm32-unknown-unknown` 릴리스 빌드 통과(약 16 KB).
- Host 코어(`wasm/`, `module/`, `bridge/`): 빌드된 실제 `.wasm`을 Chicory 1.7.5로 로드해 메타데이터, lifecycle, 로그 전달, 깨진 파일 스킵까지 실행 확인.
- 아직 미검증: Gradle 빌드와 실제 Paper 서버 구동(`PasmHost`, `plugin.yml`). 개발 환경에서 Maven 저장소 접근이 막혀 있어 실행해 보지 못했다. 아래 "Paper 서버에서 실행"으로 확인해 주세요.

## 구조

```
PASM/
├── pasm/          Rust API crate (logger, 내부 ABI 계층)
├── pasm-macros/   proc macro (#[plugin] 구현, 나머지는 Phase 2/3)
├── pasm-host/     Paper 플러그인 (Java, Gradle)
├── examples/hello 예제 플러그인
└── docs/ABI.md    ABI v1 명세
```

## 설계 결정

### WASM target: `wasm32-unknown-unknown`

| | 순수 WASM | wasm32-wasip1 |
|---|---|---|
| 호스트 구현량 | PASM import만 구현 | WASI import(fd_write, clock, random...) 추가 구현/의존 필요 |
| 샌드박스 | 기본적으로 아무 권한 없음 | 파일/시계/랜덤 등이 열려 있어 막는 작업 필요 |
| std 지원 | alloc, String, Vec, format! 사용 가능. 파일/스레드/시간은 없음 | 파일/시간 등 일부 가능 |
| 안정성 | import가 적어 ABI가 단순 | WASI 버전(p1/p2)에 묶임 |

플러그인은 Paper API로만 외부와 통신해야 하므로 WASI 권한이 오히려 불필요한 공격면이다. MVP는 순수 WASM으로 간다. 파일이나 시간이 필요해지면 PASM host function으로 제공한다.

### Runtime: Chicory (순수 Java)

- JNI/네이티브 의존성이 없어 Paper 서버 OS/아키텍처에 상관없이 동작하고, WASM 힙이 JVM 힙과 분리된다.
- 1.x 안정 릴리스(1.7.5, 2026-03)이고 활발히 유지보수된다. Apache-2.0.
- 대안(Wasmtime/Wasmer Java 바인딩)은 네이티브 라이브러리 배포와 플랫폼별 빌드가 필요하다. 성능이 병목이 되면 Chicory의 AOT(build-time compiler)나 런타임 컴파일러로 올릴 수 있고, `WasmRuntime`이 유일한 접점이라 교체 범위가 작다.
- `plugin.yml`의 `libraries`로 서버 시작 시 Maven Central에서 받으므로 shading이 필요 없다(서버가 인터넷 접근 가능해야 함. 오프라인 서버는 shadow jar로 바꿀 것).

### 제약

- `#[plugin]` struct는 `Default`가 필요하다. 매크로가 `#[derive(Default)]`를 붙이므로 직접 derive/impl 하면 충돌한다. 필드 초기값은 `on_enabled`에서 설정한다.
- `on_enabled`/`on_disabled`는 생략 가능하다(기본 no-op).
- 한 crate에 `#[plugin]`은 하나만 둘 수 있다(export 이름 중복).
- `.gitattributes` 파일명은 `.gitattribute`가 아니라 `.gitattributes`다.

## 빌드

요구 사항: Rust 1.85+ (`rustup target add wasm32-unknown-unknown`), JDK 25+, Gradle 8.14+ (wrapper는 `gradle wrapper`로 생성).

```bash
cargo test --workspace
cargo build -p hello-plugin --release --target wasm32-unknown-unknown

cd pasm-host
gradle build
```

결과물: `target/wasm32-unknown-unknown/release/hello_plugin.wasm`, `pasm-host/build/libs/pasm-host-0.1.0.jar`.

Paper API 버전은 `-PpaperApiVersion=26.1.2.build.+`로 바꿀 수 있고, 기본값은 `build.gradle.kts`에 있다.

Gradle 테스트(wasm 필요):

```bash
gradle test -PpasmTestWasm=/abs/path/to/hello_plugin.wasm
```

## Paper 서버에서 실행

1. `pasm-host-0.1.0.jar`를 서버 `plugins/`에 넣는다.
2. 서버를 한 번 켜서 `plugins/Pasm/plugins/`를 만든다.
3. `hello_plugin.wasm`을 `plugins/Pasm/plugins/`에 복사하고 서버를 재시작한다.
4. 기대 콘솔 출력:

```
[Pasm] [hello_plugin] HelloPlugin enabled (count = 1)
[Pasm] [hello_plugin] this is a warning from Rust
[Pasm] [hello_plugin] this is an error from Rust
[Pasm] Enabled HelloPlugin v0.1.0 by AS7AR (hello_plugin.wasm)
[Pasm] Loaded 1 of 1 PASM module(s)
```

서버 종료 시 `HelloPlugin disabled (count = 1)`이 출력된다.

## 플러그인 작성

```rust
use pasm::*;

#[plugin(name = "HelloPlugin", authors = ["AS7AR"], version = "0.1.0")]
struct HelloPlugin {
    enable_count: u32,
}

impl HelloPlugin {
    fn on_enabled(&mut self) {
        self.enable_count += 1;
        logger::info(format!("enabled {}", self.enable_count));
    }

    fn on_disabled(&mut self) {
        logger::info("disabled");
    }
}
```

`Cargo.toml`에는 `crate-type = ["cdylib"]`와 `pasm` 의존성을 둔다(`examples/hello` 참고). 릴리스 프로필은 루트 `Cargo.toml`의 `panic = "abort"`, `lto`, `opt-level = "s"`를 쓴다.

## 로드맵

- Phase 2: handle 테이블, `Player`, `PlayerJoinEvent`/`PlayerQuitEvent`, `#[event_listener]`, `#[event]`, `send_message`
- Phase 3: priority, ignore_cancelled, scheduler, `#[command]`
- Phase 4: World, Entity, Location, ItemStack, Block

Phase 1에서 이미 갖춰 둔 확장 지점: `__pasm_meta_<kind>` 발견 규칙, `__pasm_alloc/free`, ABI 버전 체크, `PasmMetadata`의 unknown kind 무시.
