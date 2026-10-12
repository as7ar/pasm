# PASM ABI v1

Rust 플러그인(guest)과 Paper 호스트(JVM) 사이의 계약이다. Rust API(`pasm` crate)와는 독립적이며, `#[plugin]` 등의 매크로가 이 ABI에 맞는 코드를 생성한다.

## 기본 규칙

- 타깃: `wasm32-unknown-unknown` (WASI 미사용). 모듈은 `memory`를 export 해야 한다.
- 정수 인자는 전부 `i32`/`i64`. 포인터와 길이는 guest 선형 메모리 기준 `u32`(`i32`로 전달).
- JVM 객체는 절대 guest로 넘기지 않는다. 객체는 `u64` handle로만 참조한다(Phase 2).
- 문자열은 UTF-8 `(ptr, len)` 쌍이며 NUL 종료를 가정하지 않는다. 최대 1 MiB.
- 호환성: ABI 버전이 다르면 호스트는 모듈을 로드하지 않는다. 메타데이터 `kind`가 추가되는 변경은 호환(호스트는 모르는 kind를 무시)이다.

## Guest exports

| 이름 | 시그니처 | 설명 |
|---|---|---|
| `memory` | memory | 선형 메모리 |
| `__pasm_abi_version` | `() -> i32` | 모듈이 만들어진 ABI 버전. 호스트가 제일 먼저 확인 |
| `__pasm_alloc` | `(len: i32) -> i32` | host→guest 문자열/버퍼용 할당. 실패 시 0 |
| `__pasm_free` | `(ptr: i32, len: i32)` | `__pasm_alloc`으로 받은 버퍼 해제 |
| `__pasm_meta_<kind>` | `() -> i64` | 메타데이터 JSON 조각. 반환값 `(ptr << 32) \| len` |
| `__pasm_plugin_enable` | `() -> i32` | 플러그인 인스턴스 생성 + `on_enabled`. 상태 코드 반환 |
| `__pasm_plugin_disable` | `() -> i32` | `on_disabled` + 인스턴스 해제. 상태 코드 반환 |

상태 코드: `0` OK, `2` 재진입, `3` 이미 enable됨, `4` enable되지 않음. panic은 trap으로 전달되고 호스트는 모듈을 FAILED로 표시한다(trap 이후 인스턴스는 재사용하지 않는다).

## Host imports (module `pasm`)

| 이름 | 시그니처 | 설명 |
|---|---|---|
| `log` | `(level: i32, ptr: i32, len: i32) -> i32` | level 0 debug, 1 info, 2 warn, 3 error. 반환 0 OK, -1 invalid argument |

host import는 실패를 음수 반환값으로 알리고, 잘못된 포인터 범위는 호스트가 검사해서 trap으로 처리한다.

## 메모리 소유권

- guest→host 문자열(`log`, 메타데이터): guest가 소유한다. 호스트는 호출 중에 복사만 하고 해제하지 않는다. 메타데이터 조각은 guest의 static 데이터라 해제가 필요 없다.
- host→guest 문자열(Phase 2 이후): 호스트가 `__pasm_alloc`으로 버퍼를 받아 쓰고, 소비한 guest 쪽이 `__pasm_free`로 해제한다.

## 메타데이터 발견

호스트는 `__pasm_meta_`로 시작하는 함수 export를 전부 호출해 JSON 조각을 모은다. 심볼 이름을 추측하지 않고 "export 목록 + 조각의 `kind`"로 발견한다. 각 매크로가 자기 조각을 독립적으로 export 하므로 매크로 간 상태 공유가 필요 없다.

```json
{"kind":"plugin","abi":1,"name":"HelloPlugin","authors":["AS7AR"],"version":"0.1.0"}
```

Phase 2/3에서 추가될 kind: `listener`(events: type/priority/ignoreCancelled), `command`(name/aliases/description/permission).

## Handle 설계 (Phase 2 예정)

- `u64 = (generation << 32) | slot`. slot 재사용 시 generation을 올려 use-after-free와 잘못된 handle을 검출한다.
- Event handle: 이벤트 디스패치 동안만 유효. 핸들러가 반환하면 호스트가 slot을 무효화한다.
- Object handle(Player 등): 호스트가 명시적으로 승격했을 때만 장기 유지. 모듈 disable 시 해당 모듈의 handle을 전부 해제한다.
- 모든 handle은 모듈별 테이블에 저장해 다른 모듈의 handle을 쓰지 못하게 한다.
