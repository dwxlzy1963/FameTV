# FameTV — Android TV 直播聚合 APK

把 `https://famelack.com/tv/` 打包成 Android TV 上一个原生 APK：
- 启动器菜单展示 **169 个国家 / 6723+ 频道**（来自 famelack/famelack-data 仓库）
- 双引擎播放：**ExoPlayer** 直播 m3u8（首选），失败自动回退 **WebView** 加载 famelack 页面
- 全屏沉浸 + D-pad 遥控适配（中心键播放/暂停、上下键音量、左右键快进）
- 收藏 / 历史（SharedPreferences 持久化）
- 远程 JSON 拉取 + 本地缓存（24h TTL），断网兜底内置 `channels_default.json`
- 在 APK 设置页可改远程 URL，无需重装即可换源

---

## 1. 项目结构

```
FameTV/
├── build.gradle                              # 项目级
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle                          # 模块级（含所有依赖）
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml               # 声明 Leanback 启动器
        ├── java/com/fametv/app/
        │   ├── MainActivity.java             # 频道列表 + 搜索 + 工具栏
        │   ├── PlayerActivity.java           # ExoPlayer + WebView 播放器
        │   ├── FavoritesActivity.java        # 收藏列表
        │   ├── HistoryActivity.java          # 历史记录
        │   ├── SettingsActivity.java         # 远程 URL 设置 + 强制刷新
        │   ├── adapter/ChannelAdapter.java
        │   ├── model/{Channel,ChannelListResponse}.java
        │   ├── network/ChannelRepository.java
        │   ├── storage/Prefs.java
        │   ├── ui/GridSpacingItemDecoration.java
        │   └── util/UiUtil.java
        └── res/
            ├── layout/                       # activity_main / activity_player / item_channel 等
            ├── drawable/                     # banner、logo、focus selector、icons
            ├── values/                       # colors / strings / themes
            ├── anim/                         # fade_in / fade_out
            ├── mipmap-*/                     # 各密度启动图标
            ├── raw/channels_default.json     # 离线兜底（仅 3-6 个示例频道）
            └── xml/network_security_config.xml
```

---

## 2. 部署 channels.json（必做）

APK 默认从你指定的 URL 拉取频道列表。你需要把 `build_channels_json.py` 生成的
`channels.json`（2.5MB，6723 频道）放到一个**直链 URL** 上。任选一种：

### 方案 A：GitHub Gist（推荐，免费，全球 CDN）

```bash
# 1. 生成 channels.json
python3 /home/z/my-project/scripts/build_channels_json.py \
    -o /tmp/channels.json

# 2. 在 gist.github.com 创建一个 secret gist，文件名填 channels.json
#    粘贴 /tmp/channels.json 内容保存

# 3. 拿到 raw URL，形如：
#    https://gist.githubusercontent.com/yourname/<gist_id>/raw/channels.json
```

### 方案 B：GitHub Pages（适合自己 fork 一份）

```bash
# 1. fork / 创建一个仓库，例如 yourname/fametv-channels
# 2. 把 channels.json 放到仓库根
# 3. 仓库 Settings → Pages → Source: main branch → Save
# 4. 等 1 分钟后 raw URL：
#    https://yourname.github.io/fametv-channels/channels.json
```

### 方案 C：自己的服务器 / 对象存储

直接把 channels.json 上传到任意 HTTPS 静态服务器，例如：
- 阿里云 OSS / 腾讯云 COS / 七牛云（开 CORS 即可）
- Cloudflare R2 / AWS S3
- Nginx 静态目录

**URL 要求**：
- 必须是 HTTPS
- 必须返回 `Content-Type: application/json` 或 `text/plain`
- 不能是 302 重定向到需要登录的页面

### 把 URL 写进 APK（二选一）

**方式 1（推荐）：装好后改** — APK 启动 → 设置 → 编辑 URL → 粘贴你的 raw URL → 保存 → 点"立即刷新"。

**方式 2：编译前改** — 打开 `app/src/main/java/com/fametv/app/network/ChannelRepository.java`，
找到 `DEFAULT_REMOTE_URL`，直接替换为你的 URL 后再编译。

---

## 3. 编译 APK

### 方式 A：本地 Android Studio（推荐）

1. 安装 [Android Studio Hedgehog+](https://developer.android.com/studio)（约 1GB）
2. 启动 Android Studio → `Open an existing project` → 选择 `FameTV/` 目录
3. 等 Gradle sync 完成（首次会下载 ~500MB SDK 组件，需翻墙或配镜像）
4. 顶部菜单 `Build` → `Build Bundle(s)/APK(s)` → `Build APK(s)`
5. APK 输出路径：
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
6. （可选）签名 release 版：`Build` → `Generate Signed Bundle / APK` → APK →
   创建 keystore → 选 release variant

#### 国内 Gradle 镜像加速

如果 sync 卡住，编辑 `FameTV/build.gradle` 的 `repositories`：

```groovy
maven { url 'https://maven.aliyun.com/repository/google' }
maven { url 'https://maven.aliyun.com/repository/central' }
maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
```

### 方式 B：命令行 Gradle（无 Android Studio）

```bash
cd FameTV
# 装 JDK 17
sudo apt install openjdk-17-jdk
# 下载 Android Command Line Tools
#   https://developer.android.com/studio#command-line-tools-only
# 设 ANDROID_HOME 环境变量后：
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

### 方式 C：云端在线编译（无开发环境，免本地安装）

#### C1. GitHub Actions（推荐）

1. Fork / 上传 FameTV 整个目录到 GitHub 仓库
2. 在仓库根创建 `.github/workflows/build.yml`：

```yaml
name: Build APK
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - run: cd FameTV && chmod +x gradlew && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: fametv-debug.apk
          path: FameTV/app/build/outputs/apk/debug/app-debug.apk
```

3. 推到 main → Actions tab → 跑完后下载 artifact（约 5 分钟）

#### C2. 在线 IDE（Gitpod / CodeSandbox）

- 把项目上传到 GitHub → 用 https://gitpod.io/#your-repo-url 一键开 Linux 环境
- 在 terminal 跑上面方式 B 的命令

#### C3. 第三方在线打包服务

- https://www.bitrise.io （免费 14 天）
- https://codemagic.io （免费 500 分钟/月）
- 上传项目 → 配置 Gradle 命令 → 自动产出 APK 下载链接

---

## 4. 装到 Android TV 上

### 前提：开启 ADB 调试

在 TV 上：`设置` → `关于` → 连续点 7 次 `版本号` 开启 `开发者选项` →
回到 `设置` → `设备偏好设置` → `开发者选项` → 开启 `USB 调试`（或 `网络调试`）

### 方式 A：U 盘 sideload（最简单）

1. 把 `app-debug.apk` 拷到 U 盘根目录
2. 在 TV 上装一个文件管理器（推荐 [Solid Explorer](https://play.google.com/store/apps/details?id=pl.solidexplorer2) 或 [FX File Explorer](https://play.google.com/store/apps/details?id=nextapp.fx)）
3. U 盘插入 TV → 用文件管理器打开 → 点 `app-debug.apk` → 允许安装 → 完成
4. 启动器里找到 `FameTV` 图标 → 打开

### 方式 B：ADB 网络安装（推荐给开发者）

```bash
# 1. TV 和电脑同 WiFi
# 2. 在 TV 设置里查到 TV 的 IP（例如 192.168.1.100）
adb connect 192.168.1.100:5555
adb install -r app-debug.apk
# 启动
adb shell am start -n com.fametv.app/.MainActivity
```

### 方式 C：第三方应用商店

- [Aptoide TV](https://tv.aptoide.com/) — 直接把 APK 上传后扫码安装
- [Send files to TV](https://play.google.com/store/apps/details?id=com.rhmsoft.sendfilestotv) — 手机推送 APK 到 TV

---

## 5. 遥控器操作速查

| 按键 | 主列表 | 播放器 |
|------|--------|--------|
| 方向键 | 切换焦点 / 翻页 | 上下=音量 / 左右=快进 10s |
| 中心键 OK | 打开频道 | 播放/暂停 |
| 返回 | 退出 | 退出播放（WebView 有历史时先退到上页） |
| MENU | 打开设置 | 切换播放源（ExoPlayer / WebView / YouTube） |
| Page Up/Down | 强制刷新列表 | — |
| 长按列表项 | 收藏/取消 | — |

---

## 6. 常见问题

### Q1：装上后显示"3 channels"或"6 channels"
默认内置兜底文件只有示例频道。打开 `设置 → 编辑 URL` 填入你部署的 channels.json raw URL → `立即刷新`。

### Q2：某些频道黑屏 / 不播放
- 频道源本身可能 geo-blocked（API 标注 `is_geo_blocked: true`）—— 用 VPN 或者切换网络
- m3u8 流可能已下线 —— 长按频道选 `WebView` 模式加载 famelack 原页面试试
- YouTube 嵌入有时需在 WebView 模式下点一下中心键才能自动播

### Q3：APK 装不上，提示"解析失败"
- debug 版未签名也能装，但需在 TV 设置里允许 `未知来源`
- 如果 TV 是 Android 5.0 以下不支持，本工程 minSdk=21（5.0）

### Q4：6723 频道列表加载慢
首次启动会从远程拉 2.5MB JSON，TV 网络慢的话需 10-30 秒。
后续启动直接读本地缓存（< 100ms），24h 后自动后台刷新。

### Q5：怎么更新频道列表
- 重新跑 `python3 build_channels_json.py` 生成新版 channels.json
- 覆盖上传到同一 URL
- APK 设置页点 `立即刷新`

### Q6：怎么自动每天更新
目前 APK 是 24h TTL 自动后台刷新。如要更频繁，改 `ChannelRepository.java` 里的
`CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)` 为你想要的间隔。

---

## 7. 致谢

频道数据来源：[famelack/famelack-data](https://github.com/famelack/famelack-data) （CC 协议）
播放器：[ExoPlayer / Media3](https://github.com/androidx/media)
WebView 兜底：直接加载 famelack.com 原始页面
