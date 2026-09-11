---
name: git-commit-convention
description: f-market 저장소에서 git 브랜치를 만들거나, 커밋 메시지를 작성하거나, 커밋을 수행하거나, PR을 준비할 때 반드시 사용하세요. Conventional Commits 스타일의 커밋 타입(feat/fix/refactor/test/docs/chore/style/perf)과 브랜치 명명 규칙(feature/도메인-기능, fix/이슈번호-설명 등)을 정의합니다. "커밋해줘", "커밋 메시지 뭐라고 쓸까", "브랜치 이름 어떻게 할까" 같은 요청에도 이 스킬을 사용하세요.
---

# Git / 커밋 컨벤션 (f-market)

이 저장소에서 커밋을 만들거나 브랜치를 딸 때 아래 규칙을 따릅니다. 목적은 커밋 로그만 봐도 "무슨 종류의 변경인지"와 "왜 했는지"를 바로 파악할 수 있게 하는 것입니다.

## 브랜치 명명

- `feature/도메인-기능` — 신규 기능 (예: `feature/cart-add-item`)
- `fix/이슈번호-설명` — 버그 수정 (예: `fix/123-order-cancel-stock`)
- `refactor/설명` — 동작 변경 없는 구조 개선
- `test/설명`, `docs/설명`, `chore/설명` — 테스트 보강, 문서, 빌드/설정 등 기타 변경

## 커밋 메시지: Conventional Commits 스타일

제목은 `type: 설명` 형식으로 씁니다. 타입은 다음 중 하나를 씁니다.

| type | 용도 |
|---|---|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 코드 구조 개선 |
| `test` | 테스트 추가/보강 |
| `docs` | 문서 변경 (README, CLAUDE.md 등) |
| `chore` | 빌드/설정/의존성 등 기타 변경 |
| `style` | 포맷팅 등 동작에 영향 없는 변경 (이 저장소는 Spotless가 대부분 자동 처리하므로 드물게 사용) |
| `perf` | 성능 개선 |

**예시**

```
feat: 사용자 이메일 중복 검증 로직 추가
fix: 주문 취소 시 재고 복구 안 되는 버그 수정
refactor: UserService 책임 분리
test: UserService 단위 테스트 추가
docs: README API 명세 업데이트
```

## 커밋 작성 규칙

- 제목은 명령형으로 간결하게 쓰고 끝에 마침표를 붙이지 않습니다.
- 한 커밋은 하나의 논리적 변경 단위로 유지합니다 — 포맷팅/리팩터링과 기능 변경을 한 커밋에 섞지 마세요. 섞이면 `git log`로 "이 커밋이 왜 있었는지"를 되짚기 어려워집니다.
- 제목만으로 "왜" 바꿨는지 설명이 부족하면, 빈 줄 하나를 두고 본문에 배경/이유를 씁니다. "무엇을 했는지"는 diff로 알 수 있으니 본문은 "왜"에 집중합니다.
- 기존 동작을 깨는 변경은 타입 뒤에 `!`를 붙이거나(`feat!: ...`) 본문에 `BREAKING CHANGE:` 문단을 추가해 명시합니다.
- 커밋 전에 `./gradlew spotlessCheck`(또는 `spotlessApply`)로 포맷 위반이 없는지 확인합니다 — 이 저장소는 Spotless가 `check`/`build` 태스크에 연결되어 있어 포맷이 깨지면 빌드가 실패합니다.
