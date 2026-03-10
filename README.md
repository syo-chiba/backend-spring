# backend-spring

面談予約フロー管理の Spring Boot アプリです。

## 開発用DBセットアップ（現在の構成）

このアプリの実行時DBは **MySQL 8.x** です。  
`src/main/resources/application.properties` の現在値は以下です。

- URL: `jdbc:mysql://localhost:3306/flow_scheduler?useSSL=false&serverTimezone=Asia/Tokyo&allowPublicKeyRetrieval=true`
- ユーザー: `root`
- パスワード: `rootroot`

### 1. MySQL 8.x を起動

ローカルの MySQL サーバーを `localhost:3306` で起動してください。

### 2. データベースを作成

```sql
CREATE DATABASE flow_scheduler
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

### 3. 接続情報を合わせる

そのまま使う場合は、MySQL の `root` ユーザーのパスワードを `rootroot` に合わせてください。  
別ユーザー/別パスワードを使う場合は `src/main/resources/application.properties` の以下を環境に合わせて変更してください。

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

### 4. アプリ起動

- macOS / Linux

```bash
./mvnw spring-boot:run
```

- Windows (cmd)

```bat
mvnw.cmd spring-boot:run
```

補足: テスト実行時は `src/test/resources/application.properties` で H2 (in-memory) を使います。

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

