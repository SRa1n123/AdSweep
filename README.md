# 净窗（AdSweep）

一个**只干一件事**的 Android 小工具：应用启动/切回之后那些「小窗户」广告浮层，自动帮你点掉。
不劫持启动页、不挡开屏倒计时——那个李跳跳已经做了；净窗补的是**应用内部**弹出来的浮层。

> 适用：Android 10+（本项目按 Android 17 / targetSdk 35 编译，一加 15T 等国产 ROM 可用）

## 它怎么工作

```
窗口变化 ──► 爆发期 6s（60/260/620/1100/1800/2800ms 六次扫描）
              │
              ├─ 遍历【所有】窗口的节点树（弹窗常是独立 window，只看 focused root 会漏）
              ├─ 白名单过滤（关掉的 App 直接跳过）
              ├─ 安全闸：不点 同意/支付/安装/授权 类文本，不碰输入框，长文案不点
              └─ 命中规则 ──► 点它 / 点可点击父级 / 点中心坐标 / 按返回
```

三件防误触的硬约束：**每个窗口最多 3 下、两次点击间隔 ≥1.1s、命中后本轮立即停止**。

## 安装

工程已内置 GitHub Actions，推送即出 APK：

1. 新建一个空仓库，把本目录推上去：
   ```bash
   cd AdSweep
   git init && git add . && git commit -m "init"
   git remote add origin https://github.com/<你>/AdSweep.git
   git push -u origin main
   ```
2. 仓库 **Actions** 页 → 等 `build-apk` 跑完（约 3–5 分钟）。
3. 进这个 run → 底部 **Artifacts** → 下载 `AdSweep-debug-apk` → 解压得到 `app-debug.apk` → 传到手机安装（需允许「安装未知应用」）。

本地编译（有 Android Studio 时）：直接 `Open` 本目录，或用命令行 `gradle assembleDebug`。

## 用法

| 步骤 | 做什么 |
| --- | --- |
| 1 | 打开 App → 点「开启无障碍服务」→ 在系统设置里找到 **净窗** 打开 |
| 2 | 正常用手机。广告浮层出现时它自己点 |
| 3 | 某个 App 的关闭按钮认不出来 → 打开那个 App 等广告弹出来 → 切回净窗点 **学习模式** → 「重新抓一次屏幕」→ 点那一行 → 保存 |
| 4 | 不想让某个 App 被处理 → **按应用启用** → 关掉它 |
| 5 | 自己加一条通用规则 → 首页 **添加一条全局规则** |

首页顶部是总开关：关掉即完全停手。规则行右侧开关可单条停用，**长按删除**。

## 为什么它能点到「应用内弹窗」

李跳跳那类工具主要靠**点开屏的「跳过」**，而 PU 口袋校园这类是在**应用里**用小窗口(悬浮/半屏)弹广告：
这类窗口往往

- 是**独立的 application window**（不是当前 Activity 的子树）→ 所以净窗遍历 `getWindows()` 而不是只取 `rootInActiveWindow`；
- 出现时间**不固定** → 所以窗口一变就进 6 秒爆发期，而不是只扫一次；
- 关闭按钮可能**只是个没有文字的图标**（×/✕）→ 所以支持「文本等于 ×」「ID 正则」「描述包含」，并且允许空文本节点由规则本身限定；
- 关闭按钮自己**不可点击**（点击事件挂在父容器上）→ 所以默认动作是「点可点击父级」，失败再退回坐标点按。

## 隐私

- 不申请网络权限（`AndroidManifest.xml` 里没有任何网络权限）。
- 不截图、不录屏、不读输入内容（`canRetrieveWindowContent` + 排除 editable 节点）。
- 规则和统计只存在本机 SharedPreferences。
- 无障碍权限是系统级敏感权限：只在你主动开启时生效，关掉总开关立即停手。

## 目录结构

```
app/src/main/java/com/jingchuang/sweep/
├── MainActivity.kt          首页：状态、总开关、规则列表、加规则
├── AppsActivity.kt          按应用启停（白名单）
├── LearnActivity.kt         学习模式：抓当前界面节点，指哪打哪
├── service/SweepService.kt  引擎：事件 → 爆发期 → 多窗口扫描 → 点击
├── core/Rule.kt             规则模型（pkg + kind + value + action）
├── core/RuleStore.kt        规则读写 + 内置种子规则
├── core/NodeScanner.kt      节点遍历与匹配（含正则缓存）
├── core/ClickGuard.kt       安全闸：绝不点同意/支付/安装
└── util/{Prefs,Ui}.kt       配置与小 UI 工具
```

## 已知限制

- **游戏内/Flutter/RN 自绘界面**可能整棵树都是空节点，规则无从匹配（这种情况只能靠坐标或返回键规则）。
- 部分广告 SDK 会检测无障碍并延迟弹出关闭按钮，爆发期 6 秒可能不够——可在 `SweepService.BURST_MS` 调大。
- 国产 ROM（ColorOS/OriginOS 等）可能杀后台无障碍服务，建议在系统设置里给它「自启动/后台不优化」。
- 规则匹配靠文本与 id，广告改文案就会失效；「学习模式」是最快的补救方式。

## 免责

仅供个人学习与自用，用于跳过你设备上不想看的商业弹窗。请勿用于自动化点击、刷量等违反平台规则的行为。
