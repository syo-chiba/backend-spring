# backend-spring

面談予約フロー管理の Spring Boot アプリです。

## テスト実行

### 全テスト

- macOS / Linux

```bash
./mvnw test
```

- Windows (cmd)

```bat
mvnw.cmd test
```

### 個別テスト実行

```bash
./mvnw -Dtest=FlowServiceTest test
./mvnw -Dtest=FlowControllerTest test
./mvnw -Dtest=SecurityStaticResourceAccessTest test
```

### 個別メソッド実行

```bash
./mvnw -Dtest=FlowServiceTest#createFlow_shouldRejectOutOfRangeStartFrom test
```

## テスト方針メモ

- `FlowControllerTest` は JDK 25 環境での Mockito inline mock 互換性問題を避けるため、`FlowService` をモックせず `StubFlowService` を使っています。
- そのため、具象クラスの inline mocking に依存せず、環境差分の影響を受けにくい構成です。

