# 发布到 GitHub 完整指南

本指南带你从零开始把 FameTV 项目发布到 GitHub，包括：
- 创建仓库
- 推送代码
- 用 GitHub Actions 自动编译 APK
- 用 GitHub Pages 免费托管 channels.json
- 发布 Release 供他人下载 APK

---

## 0. 前置准备

| 工具 | 说明 |
|------|------|
| Git | 已安装（本机 `git --version` 应返回 2.x+） |
| GitHub 账号 | 注册 https://github.com/signup |
| SSH key 或 PAT | 用于认证推送（二选一，下面会讲） |

可选：
- GitHub CLI（`gh`）— 命令行直接创建仓库，省事
- Android Studio — 想本地改代码时用

---

## 1. 创建 GitHub 仓库

### 方式 A：网页创建（最简单）

1. 打开 https://github.com/new
2. 填表：
   - **Repository name**：`FameTV`（或任意名字）
   - **Description**：`Android TV live TV aggregator APK for famelack.com/tv`
   - **Public** 或 **Private**：建议 Public（这样 GitHub Pages 免费，Actions 也免费）
   - **不要**勾选 "Add a README file" — 仓库里已经有了
   - **不要**勾选 "Add .gitignore" — 已经有了
   - **不要**勾选 "Choose a license" — 已经有 MIT 了
3. 点 `Create repository`
4. 复制页面上显示的仓库 URL（形如 `https://github.com/yourname/FameTV.git`）

### 方式 B：用 `gh` CLI 命令行创建（一次完成）

先安装 gh：
```bash
# macOS
brew install gh

# Linux (Debian/Ubuntu)
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null
sudo apt update && sudo apt install gh

# Windows
winget install --id GitHub.cli
```

登录 + 创建仓库：
```bash
gh auth login                              # 浏览器扫码登录
gh repo create FameTV --public --source=. --remote=origin --push
```

一行搞定。

---

## 2. 配置认证（HTTPS 推送需要 PAT）

GitHub 早已不支持密码推送，必须用 **Personal Access Token (PAT)**：

1. 打开 https://github.com/settings/tokens?type=beta
2. 点 `Generate new token` → `Fine-grained token`
3. 配置：
   - **Token name**：`FameTV push`
   - **Expiration**：`90 days`（或 `No expiration`）
   - **Repository access**：`All repositories`（或只选刚才那个仓库）
   - **Permissions** → `Repository permissions`：
     - `Contents`：Read and write
     - `Metadata`：Read-only（自动勾选）
     - `Workflows`：Read and write（要让 Actions 跑起来必须勾）
4. 点 `Generate token`
5. **立即复制 token**（页面关闭后看不到了），形如 `github_pat_xxxxxxxxxxxxx`

把 token 存好，下面要用。

### 缓存 token 避免每次输入

```bash
# Linux / macOS
git config --global credential.helper store
# 第一次推送时输入用户名 + token（不是密码），之后就自动缓存

# Windows
git config --global credential.helper manager
```

### 或者用 SSH key（更安全、一次配置永久用）

```bash
# 1. 生成密钥（如有跳过）
ssh-keygen -t ed25519 -C "your_email@example.com"
# 一路回车，密码可留空

# 2. 查看公钥
cat ~/.ssh/id_ed25519.pub

# 3. 复制输出，添加到 GitHub:
#    https://github.com/settings/keys → New SSH key → 粘贴 → Save

# 4. 测试
ssh -T git@github.com
# 看到 "Hi yourname! You've successfully authenticated" 就 OK
```

---

## 3. 推送代码

### 一键脚本（推荐）

仓库根目录已有 `push_to_github.sh`，直接跑：

```bash
cd FameTV

./push_to_github.sh your-github-username FameTV
# 例：./push_to_github.sh johnsmith FameTV
```

脚本会自动：
- 检测是否有 SSH key，没有则用 HTTPS
- 添加 remote `origin`
- `git push -u origin main`

### 手动命令版

```bash
cd FameTV

# HTTPS 方式
git remote add origin https://github.com/yourname/FameTV.git
git push -u origin main

# 或 SSH 方式
git remote add origin git@github.com:yourname/FameTV.git
git push -u origin main
```

第一次推送会要求输入：
- **Username**：你的 GitHub 用户名
- **Password**：粘贴刚才的 PAT（不是 GitHub 登录密码）

推送成功后打开 https://github.com/yourname/FameTV 应该能看到所有代码。

---

## 4. 自动编译 APK（GitHub Actions）

仓库已配置 `.github/workflows/build.yml`，推送到 main 后会自动触发。

### 查看构建状态

1. 打开仓库页面
2. 顶部点 **Actions** tab
3. 看到最新的 workflow run "Build APK"
4. 等约 5-8 分钟，绿勾表示成功

### 下载编译好的 APK

1. 进入成功的 workflow run
2. 滚到底部 **Artifacts** 区域
3. 点 `FameTV-debug-apk` 下载 → 得到 `FameTV-debug-apk.zip`
4. 解压得到 `app-debug.apk`
5. 拷到 U 盘 sideload 到 Android TV

如果构建失败，点进 run 看红色那一步的日志，常见问题：
- `SDK license not accepted` → 在你的本地先 `sdkmanager --licenses`，commit `local.properties`（已被 .gitignore 忽略，需要手动）

---

## 5. 免费托管 channels.json（GitHub Pages）

APK 装好后要让它能拉到全部 6723 频道，需要把 `channels.json` 暴露成一个公网 URL。

### 启用 GitHub Pages

1. 仓库页面 → **Settings** → 左侧 **Pages**
2. **Source** 选 `Deploy from a branch`
3. **Branch** 选 `main` / `/data` 文件夹（channels.json 在 `data/` 下）
4. 点 **Save**
5. 等 1-2 分钟，刷新页面会显示：
   ```
   Your site is live at https://yourname.github.io/FameTV/
   ```
6. 你的 channels.json URL 就是：
   ```
   https://yourname.github.io/FameTV/channels.json
   ```
   （注意：因为选了 `/data` 文件夹作为 Pages 根，路径是相对 `/data/` 的）

   如果想用根路径，把 `data/channels.json` 移到仓库根目录，再 Pages → Branch：main / root。

### 在 APK 里填这个 URL

1. 打开 APK
2. **设置 → 编辑 URL**
3. 粘贴 `https://yourname.github.io/FameTV/channels.json`
4. 保存 → 立即刷新
5. 等 2-5 秒，6723 频道全部加载

### 更新频道列表

```bash
# 本地
python3 data/build_channels_json.py -o data/channels.json
git add data/channels.json
git commit -m "chore: refresh channels.json"
git push

# GitHub Pages 1-2 分钟后自动更新
# APK 启动时检测到 24h TTL 过期会自动重新拉取
```

---

## 6. 发布 Release（让别人下载 APK）

GitHub Actions 的 artifact 默认只保留 90 天且需要登录才能下。要长期公开分发，发个 Release。

### 方式 A：手动上传

1. 从 Actions 下载 APK 到本地
2. 仓库页面点 **Releases** → **Create a new release**
3. **Choose a tag**：输入 `v1.0.0` → 点 `Create new tag: v1.0.0 on publish`
4. **Release title**：`FameTV v1.0.0`
5. **Description**：写更新说明
6. 拖拽 `app-debug.apk` 到 **Attach binaries** 区域
7. **Publish release**

发布后任何人都可以从 `https://github.com/yourname/FameTV/releases` 直接下载 APK，无需登录。

### 方式 B：自动化发版（进阶）

在 `.github/workflows/` 新建 `release.yml`：

```yaml
name: Release APK

on:
  push:
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - run: chmod +x ./gradlew && ./gradlew assembleRelease
      - uses: softprops/action-gh-release@v1
        with:
          files: app/build/outputs/apk/release/app-release.apk
          generate_release_notes: true
```

打 tag 自动发版：
```bash
git tag v1.0.0
git push origin v1.0.0
# GitHub 自动构建 + 发布 Release
```

---

## 7. 常见问题

### Q1：推送时报 `Permission denied (publickey)`

SSH key 没配好。两个选择：
- 按 §2 配 SSH key
- 或改用 HTTPS：`git remote set-url origin https://github.com/yourname/FameTV.git`

### Q2：推送时报 `Authentication failed`

密码位置应该填 PAT 而不是 GitHub 登录密码。重新生成 PAT（勾选 `repo` + `workflow` 权限）。

### Q3：Actions 报错 `Could not determine java version`

JDK 配置问题。`.github/workflows/build.yml` 里已经固定 `java-version: '17'`，应该没问题。如果还报错，把 distribution 改成 `zulu` 试试。

### Q4：Actions 跑了但没生成 APK artifact

打开失败的 run 看日志，找 `BUILD FAILED` 那一行。最常见原因：
- 某个依赖下载失败 → 重新触发 workflow（点 Re-run）
- 代码有语法错误 → 修了重新 push

### Q5：GitHub Pages 提示 `404`

确认：
- Settings → Pages 显示 "Your site is live"
- 仓库根目录或 `data/` 下确实有 `channels.json`
- 等 2 分钟 CDN 同步
- URL 路径正确（选 `/data` 时是 `https://yourname.github.io/RepoName/channels.json`，不是 `/data/channels.json`）

### Q6：channels.json 太大（2.5MB），GitHub 不让提交？

GitHub 单文件限制 100MB，2.5MB 没问题。但如果你不想把数据放仓库里，可选：
- Git LFS：`git lfs track "data/channels.json"` 后再 add/commit
- 单独建一个 gist，APK 配置 gist raw URL
- 用 Releases 作为附件上传

### Q7：不想暴露源码，只想发布 APK

设为 **Private** 仓库即可。但 Private 仓库的 GitHub Pages 和 Actions 是有限的（免费版 Actions 2000 分钟/月，Pages 仅 Public 免费）。建议 Public。

---

## 8. 仓库结构总览

推送完成后，你的仓库长这样：

```
FameTV/                          https://github.com/yourname/FameTV
├── .github/workflows/
│   ├── build.yml                ← 推送时自动编译 APK
│   └── release.yml              ← （可选）打 tag 自动发版
├── app/                         ← Android 项目源码
├── data/
│   ├── channels.json            ← 6723 频道数据（GitHub Pages 暴露）
│   └── build_channels_json.py   ← 重新生成 channels.json 的脚本
├── docs/
│   └── PUBLISH_TO_GITHUB.md     ← 本文档
├── tools/
│   └── gen_icons.py             ← 图标生成脚本
├── README.md                    ← 详细使用文档
├── LICENSE                      ← MIT
├── push_to_github.sh            ← 一键推送辅助脚本
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
└── local.properties.example     ← 改名为 local.properties 填 SDK 路径
```

---

## 9. 一句话流程

```bash
# 假设你已经在 FameTV/ 目录，且本地 git 已 init + commit

# 1. 在 https://github.com/new 创建空仓库（不勾选任何模板）

# 2. 推送
./push_to_github.sh your-username FameTV

# 3. 启用 GitHub Pages
#    Settings → Pages → Source: main / /data → Save
#    得到 URL: https://your-username.github.io/FameTV/channels.json

# 4. APK 装到 TV 后，设置里填这个 URL，立即刷新
```

完事。整个流程约 10-15 分钟。
