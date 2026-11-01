# GhostSam

**中文** | [English](README.md)

面向三星 Galaxy S22 / S26 / Z Fold8 的内核提权工具包（Android 12 及以上，
内核 5.10-6.18），附带一个驱动全流程的 Android 管理端 App —— 走内置无线
调试（无需电脑）或 Shizuku。

源码树包含三条按机型划分的 Ghostlock 漏洞利用链（竞争性利用）、一条按内核
版本匹配的 dirtyfrag 安装链、一个共享的用户态层，以及负责打包预构建 payload
并编排每次运行的管理端 App。

## 概览

- **目标机型**：Galaxy S22（`s22`）、Galaxy S26（`s26`）、Galaxy Z Fold8
  （`zf8`），覆盖 Snapdragon 与 Exynos 平台。
- **安装路径**：
  - **dirtyfrag（推荐，更快）**：单次运行即完成——经由 xfrm-ESP 页缓存
    4 字节写（CVE-2026-43284）部署 KernelSU 内核模块并直接 late-load，无需
    赢下竞争。独立模式从首页卡片直接运行，无需组件、无需 ADB。
  - **Ghostlock 链**：面向所选系列的竞争性利用；运行过程中设备会重启，之后
    由 App 完成 KernelSU late-load。
- **内存破坏原语（CVE-2026-43499）**：futex PI 释放后使用竞争被转化为一次
  受控的 8 字节内核写；以 `misc` / `simple_attr` 文件描述符作为写目标，由此
  构建任意内核读写。Z Fold8 链使用基于 `io_submit` 的写入路径。
- **提权阶段**：伪造的 `work_struct` 被挂入 `system_unbound_wq`，函数指针为
  `call_usermodehelper_exec_work`，内核即以 init 凭据启动我们的 helper；不修改
  任何凭据结构，KDP 的 EL2 守卫无从察觉。
- **Root 通道**：`su_daemon` 在 `/data/local/tmp/temp_su.sock` 上维持会话级
  root shell。
- **持久化（临时 root）**：late-load KernelSU 或 KernelSU-Next，root 保持到下次
  重启。App 的开机重获取提醒（更多 → 支持）会在重启后发出一条点按即运行的通知
  —— 不会在无人操作时自动执行。Ghostlock 链中 `ksud` 被 bind-mount 覆盖到一个
  休眠系统二进制上，基于路径的 DEFEX 规则只会看到白名单目标。

## App 功能

- **两种提权方案**：Ghostlock（利用链 + KernelSU late-load）与 DirtyFrag
  （独立模式，JNI 编译进 App 的链，按 KMI 选模块）。
- **自持通道**：内置无线调试配对/连接（libadb-android，通知栏输入配对码，
  可选 TCP 常驻）或 Shizuku —— 全程不需要电脑。
- **运行控制台**：实时日志、停滞/总超时、通道自愈、boot 回执、可导出的运行
  历史（日志 / ZIP）。
- **失败诊断**：每次失败运行自动追加一段逐行防护的 `[diag]` 证据包（设备、
  内核、系列匹配、阶段、退出码、boot 状态、开机时长/负载）—— 从运行日志里
  取出来直接贴进 issue。
- **设备报告**：更多 → 支持 →「复制设备报告」，一键复制可直接粘贴的
  Markdown 表格（代号、编译号、指纹、内核、KMI、匹配证据），用于适配请求。
- **开机重获取提醒**：更多 → 支持 中的开关；重启后发出点按即重新运行的通知。
- **Payload 配置**：本地 preload.so / ksud / su_daemon / ko 组合，按配置统计
  运行次数与成败。
- **系统工具**：三星隐藏菜单（频段选择、服务模式、场测…）、SELinux 切换、
  ksud late-load 升级、交互终端（su / 无线调试 / Shizuku）。
- **自定义内核线**：按编译号的参数表、同线兜底，或在 App 内手动编辑完整的
  17 字段自定义线（十六进制），格式由 `exploit/src/common/params_custom.h`
  校验。
- **界面**：Jetpack Compose + Miuix，English / 简体中文，动态取色、深浅色、
  界面缩放。

## 支持的设备

### Ghostlock 链（按设备匹配）

固件按内核基线匹配，而非按编译号。每个系列附带一张生成的表：每个内核基线
一条记录（17-18 个符号偏移），每个已知编译号一行；运行时在 系列 / 代号 /
型号 / 编译号 上匹配，未知组合 fail-closed（不尝试任何内核写）。

| 系列    | 基线数 | 已知编译号 | 设备 / 说明                                                |
| ------- | ------ | ---------- | ---------------------------------------------------------- |
| s22     | 4      | 66         | SM-S9010 / S9060 / S9080 ...（Snapdragon、Exynos 2200）    |
| s26     | 4      | 153        | SM-S9420 / S9470 / S9480 ...（Snapdragon、Exynos），One UI 8.5 |
| s26-ui9 | 2      | 6          | One UI 9 布局，由 io_submit 链执行                          |
| zf8     | 6      | 73         | SM-F9710 / F9760 ...（Z Fold8）                             |

未收录固件可在运行时通过 `/data/local/tmp/ghostsam-lines.conf` 提供自定义
基线；格式由 `exploit/src/common/params_custom.h` 校验。

### dirtyfrag 链（按 KMI 匹配）

dirtyfrag 链为每个 KMI 附带一个内核模块（App assets 中的
`dfr_lkm-<kmi>.ko`）；App 按设备内核选择对应模块。已附带 KMI：

| KMI            | 状态     | 说明                                         |
| -------------- | -------- | -------------------------------------------- |
| android12-5.10 | 已测试   |                                              |
| android13-5.10 | 未测试   |                                              |
| android13-5.15 | 已测试   |                                              |
| android14-5.15 | 未测试   |                                              |
| android14-6.1  | 部分支持 | 仅 ≤6.1.71 的内核；6.1.72+ 不支持            |
| android15-6.6  | 已测试   |                                              |
| android16-6.12 | 已测试   |                                              |
| android17-6.18 | 未测试   |                                              |

## 仓库结构

```
app/                             Android 管理端 App（Kotlin / Jetpack Compose / Miuix）
  app/src/main/assets/payloads/  打进 APK 的预构建 payload
  app/src/main/java/.../data/    通道（adb/Shizuku）、exploit 编排、设备匹配、
                                 诊断、开机接收器、偏好存储
  app/src/main/java/.../ui/      Compose 页面（首页、执行、历史、工具、设置、更多）
exploit/
  src/common/                    共享层：日志、选项、参数表、KernelSnitch 探针、
                                 KSU 协议、su_daemon、boot 日志、链骨架
  src/chains/pselect/            pselect 写入链 + attr 载体 + UMH 提权
  src/chains/iosubmit/           io_submit 写入链（zf8、One UI 9 上的 s26）
  src/chains/dirtyfrag/          xfrm-ESP 页缓存写入安装器 + LKM 加载器
                                 （经 JNI 编译进 App）
  src/series/s22|s26|zf8/        各系列入口、参数表与汇编
  tests/                         宿主机自测（任意机器可跑，无需设备）
```

## 构建

### App

所有 payload 已预构建在 `app/app/src/main/assets/payloads/` 下，APK 可独立
构建：

```bash
cd app
./gradlew assembleRelease
```

构建期间 `:app:verifyPayloads` Gradle 任务会校验 payload 集：每个系列目录
必须提供 `preload.so` 与 `su_daemon`，`payloads/ksud/` 必须提供 `ksud` 与
`ksud-next`，`payloads/dirtyfrag/` 必须为每个受支持的 KMI 提供一个
`dfr_lkm-<kmi>.ko`（dirtyfrag 用户态链经 JNI 编译进 App，不作为独立二进制
打包）。

### 原生 payload

构建原生部分需要 Android NDK r30（或带 aarch64 sysroot 的宿主机 clang）。
`exploit/Makefile` 是唯一构建入口；产物落在 `exploit/build/`：

```bash
cd exploit
make            # 全部系列 payload（s26 / s22 / zf8）
make s26        # 或单独目标：s26 / s22 / zf8
make test       # 宿主机自测（无需设备 / NDK）
```

`make test` 会把 `exploit/tests/options_selftest.c` 与真实的 `options.c`
一起编译并在宿主机上运行：锁定配置层优先级规则（default < file < env <
CLI，成对 env 的 reload 语义），一旦回归重新引入 env 覆盖 CLI 的遮蔽 bug
即构建失败。一个开箱即用的 GitHub Actions 工作流（每次 push 跑该自测 +
`./gradlew assembleDebug`）随 PR #10 附带 —— 放入 `.github/workflows/`
即可启用 CI。

### Dirtyfrag 内核模块

LKM 是 out-of-tree kbuild 目标，需要一份准备好的内核构建目录；Android DDK
容器按 KMI 提供：

```bash
make lkm KDIR=/path/to/kernel-build-dir
```

### ksud 二进制

`payloads/ksud/ksud` 与 `payloads/ksud/ksud-next` 是来自我们 KernelSU fork
的预构建 release（aarch64-linux-android）：
[snothin/KernelSU](https://github.com/snothin/KernelSU) 与
[snothin/KernelSU-Next](https://github.com/snothin/KernelSU-Next)；版本升级时
从这些仓库重新构建。

## 使用

1. 在受支持的设备上安装 APK（或按上文自行构建）。
2. 在设置中选择方案：
   - **DirtyFrag（推荐）**：从首页卡片直接运行 —— 无需组件、无需调试通道。
   - **Ghostlock**：在 App 内配对无线调试（或授权 Shizuku），然后点击首页
     卡片。竞争运行期间请保持屏幕常亮。
3. 可选：开启「开机后提醒重新获取 root」（更多 → 支持），重启后即可点按
   通知一键重新运行。

## 运行时选项

payload 读取一组由 App 侧 JNI/exec 链注入的 `GHOSTSAM_*` 环境变量 —— 例如
`GHOSTSAM_NO_KSU`、`GHOSTSAM_KSU_DEFER`、`GHOSTSAM_PARAMS_STRICT`。权威列表
与语义见 payload 源码（`exploit/src/common/options.*`）；App 界面只暴露安全
子集。

## 提交 issue

- 附上运行日志（历史 → 详情 → 导出）。失败的运行在末尾已携带 `[diag]`
  证据包。
- 设备适配请求请粘贴 更多 → 支持 →「复制设备报告」的内容 —— 适配所需的
  字段都在里面。
- 每个设备系列一个 issue；注明你跑的是 Ghostlock 还是 DirtyFrag，以及确切
  的失败行（`[-] ...`）。

## 与 DFRoot 的关系

@diabl0w 的 [DFRoot](https://github.com/diabl0w/DFRoot) 是公开 DirtyFrag
技术的精简实现。GhostSam 将更早期的工作（面向 S22 / S26 / Z Fold8 的
Ghostlock 利用链）整合进一个工具包，项目整体更大也更复杂 —— 但其
dirtyfrag 路径依然很快：单次运行即部署并 late-load KernelSU。欢迎试用，
欢迎反馈、issue 与 PR。

两种实现基于同一公开技术；出处声明见 [NOTICE](NOTICE)。

## 免责声明

本仓库仅用于安全研究与教育，目标是你自己拥有并控制的设备。运行 Ghostlock
链会使设备重启，可能让 SELinux 在会话内保持 permissive（Knox 看门狗可能
强制重启），强行使用错误的内核基线可能永久变砖。你需对自己的设备负责，
并遵守适用法律。

## 致谢

- **s26 系列** —— 合并自 [ghostlock-s26](https://github.com/snothin/ghostlock-s26)；
  完整致谢名单位于其 README。
- **Z Fold8 移植输入（io_submit 入门）** —— 来自 @diabl0w。
- **ksud / ksud-next** —— 构建自 [snothin/KernelSU](https://github.com/snothin/KernelSU)
  与 [snothin/KernelSU-Next](https://github.com/snothin/KernelSU-Next)，这两个 fork
  跟踪上游并吸纳了 KernelSU 社区多位开发者的工作。
- **dirtyfrag 链** —— 遵循公开的 DirtyFrag 技术；出处声明见 [NOTICE](NOTICE)。

## 许可证

Apache-2.0 —— 见 [LICENSE](LICENSE)。第三方声明与致谢见 [NOTICE](NOTICE)。
