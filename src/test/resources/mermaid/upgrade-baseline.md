# Mermaid Upgrade Baseline

本文档用于 Mermaid 升级后的手工验收基线，覆盖预览、HTML 导出和 PDF 导出三条链路。

## Legacy Flowchart

```mermaid
flowchart TD
    Start([Start]) --> Input[/Collect Markdown/]
    Input --> Render{Render Mermaid}
    Render -->|Success| Svg[Emit SVG]
    Render -->|Failure| Error[Show Error]
```

## Legacy Sequence

```mermaid
sequenceDiagram
    participant User as User
    participant Plugin as Markdown Editor
    participant Preview as JCEF Preview
    User->>Plugin: Edit markdown
    Plugin->>Preview: Apply markdown
    Preview-->>Plugin: previewRendered
```

## Legacy Gantt

```mermaid
gantt
    title Mermaid Upgrade Timeline
    dateFormat  YYYY-MM-DD
    section Upgrade
    Replace static assets :done, a1, 2026-08-10, 1d
    Verify preview/export :active, a2, after a1, 2d
```

## Mindmap

```mermaid
mindmap
  root((Mermaid 11.6.0))
    Preview
      JCEF
      Theme switching
    Export
      HTML
      PDF
    Guardrails
      Version markers
      Wiring checks
```

## Quadrant Chart

```mermaid
quadrantChart
    title Verification Focus
    x-axis Low automation --> High automation
    y-axis Low risk --> High risk
    quadrant-1 Manual preview
    quadrant-2 Manual export
    quadrant-3 Resource wiring
    quadrant-4 Version drift
    "default.html wiring": [0.84, 0.38]
    "version markers": [0.92, 0.24]
    "preview smoke": [0.28, 0.78]
    "pdf smoke": [0.36, 0.88]
```

## Zoom Stress Flowchart

```mermaid
flowchart LR
    A[Zoom Stress Flowchart] --> B[Collect current preview DOM and async Mermaid render result]
    B --> C[Decorate .language-mermaid blocks without wrapping outer containers]
    C --> D[Inject top-right zoom trigger only after SVG render succeeds]
    D --> E[Open in-page floating viewer instead of resizing the whole IDE window]
    E --> F[Clone the original SVG so source mapping and export DOM stay untouched]
    F --> G[Provide zoom in, zoom out, reset and close actions in the viewer toolbar]
    G --> H[Handle mouse wheel zoom inside the viewer viewport with bounded scale]
    H --> I[Isolate viewer wheel, click and selection events from preview sync listeners]
    I --> J[Keep HTML export and PDF export output free of viewer-only DOM fragments]
```

## Label Wrap State Diagram

状态图转移标签里的 `\n` 必须拆成完整的两行，不能在标签框右缘被裁掉。

```mermaid
stateDiagram-v2
    [*] --> Lobby: 大厅 1001
    Lobby --> Middle: 点击搜索区\n(A 组)
    Lobby --> Lobby: 点外侧更多图标\n现有 RefreshPopupMenu
    Middle --> Sug: 输入有效字符
    Sug --> Middle: 清空全部输入
    Middle --> Result: 点 Search / IME /\n历史词 / 空输入用当前暗词
    Sug --> Result: 点 Search / IME / 联想词
    Result --> Sug: 改搜索词（未提交）
    Sug --> Result: 再提交，刷新结果
    Middle --> Lobby: Back / 导航返回
    Sug --> Lobby: Back（跳过中间页）
    Result --> Lobby: Back（跳过中间页）
    Middle --> GamePlay: 推荐卡点击 H5
    Sug --> GamePlay: 点击游戏或 Play
    Result --> GamePlay: 点击游戏或 Play
    GamePlay --> [*]: 详情/游玩页
```

## Label Wrap Module Flowchart

多行节点和无空格边标签必须完整可见，对应搜索方案模块图里被裁切的标签。

```mermaid
flowchart TB
    subgraph host [游戏中心 APK]
      GCLobby["GamesLobbyFragment\nfragment_gc_games_lobby.xml"]
      GCSearch["biz_search.SearchActivity\n本期不改"]
      Galileo["GalileoExperimentManager"]
    end

    subgraph sdk [GameLobbySdk]
      API["gameslobby-api\nISearchPageService\nSearchLaunchParams"]
      Lobby["gameslobby-lobby\nGamesLobbyMainView 换搜索栏"]
      SearchMod["gameslobby-search 新模块\nMiniGameSearchActivity"]
      Common["gameslobby-common\nLoadingPageStateLayout\nGamesLobbyTrack\nH5AppDto"]
      Found["gameslobby-foundation\nLazyRxHttp NetHost\nJumpSdkStorageManager"]
      Detail["gameslobby-detail\nJumpProxy / GamePlayActivity"]
      Agg["gameslobby 聚合\n只 registerIfAbsent NoOp"]
    end

    GCLobby --> Lobby
    Galileo --> Agg
    Lobby -->|"ISearchPageService.openSearch"| API
    API --> SearchMod
    SearchMod --> Common
    SearchMod --> Found
    SearchMod -->|"IDetailPageService.jumpPlayActivity"| Detail
    Agg -->|"registerIfAbsent NoOp"| API
    SearchMod -->|"Provider 覆盖 NoOp"| API
    SearchMod -.->|"二期插槽 SearchForeignTabHost"| GCSearch
```
