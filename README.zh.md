# GhostSam

**中文** | [English](README.md)

面向三星 Galaxy S22 / S26 / Z Fold8 的内核提权工具包（Android 12 及以上，
内核 5.10-6.18），附带一个通过 ADB 驱动全流程的 Android 管理端 App。

源码树包含三条按机型划分的 Ghostlock 漏洞利用链（竞争性利用）、一条按内核
版本匹配的 dirtyfrag 安装链、一个共享的用户态层，以及负责打包预构建 payload
的管理端 App。

## 概览

- **目标机型**：Galaxy S22（`s22`）、Galaxy S26（`s26`）、Galaxy Z Fold8
  （`zf8`），覆盖 Snapdragon 与 Exynos 平台。
- **安装路径**：
  - **dirtyfrag（推荐，更快）**：单次运行即完成——经由 xfrm-ESP 页缓存
    4 字节写（CVE-2026-43284）部署 KernelSU 内核模块并直接 late-load，无需
    赢下竞争。
  - **Ghostlock 链**：面向所选系列的竞争性利用；运行过程中设备会重启，之后
    由 App 完成 KernelSU late-load。
- **内存破坏原语（CVE-2026-43499）**：把一个 futex PI 释放后使用（UAF）竞争
  转化为一次受控的 8 字节内核写；以 `misc` / `simple_attr` 文件描述符作为写
  载体，进而构建任意内核读写。Z Fold8 链使用基于 `io_submit` 的写路径。
- **root 阶段**：在 `system_unbound_wq` 上伪造一个 `work_struct`（函数为
  `call_usermodehelper_exec_work`），由内核以 init 身份启动我们的 helper；
  不修改凭证结构体，KDP 的 EL2 守卫无从触发。
- **root 通道**：`su_daemon` 在 `/data/local/tmp/temp_su.sock` 上维持一个
  root shell，贯穿整个会话。
- **持久化（临时 root）**：late-load KernelSU 或 KernelSU-Next，root 保持到
  下次重启为止（重启后重新跑链即可恢复）；Ghostlock 链中 `ksud` 以 bind
  mount 覆盖一个休眠的系统二进制，使基于路径的 DEFEX 规则看到的始终是白
  名单目标。

## 支持的机型

### Ghostlock 链（按机型匹配）

固件按内核基线匹配，而非逐 build 硬编码。每个系列携带一张生成的表：每个内核
基线一条偏移记录（17-18 个符号偏移），每个已知 build 一行；运行期按
series / codename / model / build 四个维度匹配，未收录的组合一律 fail-closed
（不发起任何内核写）。

| 系列    | 基线数 | 已知 build 数 | 机型 / 备注                                              |
| ------- | ------ | ------------- | -------------------------------------------------------- |
| s22     | 4      | 66            | SM-S9010 / S9060 / S9080 ...（骁龙、Exynos 2200）        |
| s26     | 4      | 153           | SM-S9420 / S9470 / S9480 ...（骁龙、Exynos），One UI 8.5 |
| s26-ui9 | 2      | 6             | One UI 9 布局，由 io_submit 链执行                       |
| zf8     | 6      | 73            | SM-F9710 / F9760 ...（Z Fold8）                          |

未收录固件可在运行期通过 `/data/local/tmp/ghostsam-lines.conf` 提供自定义
基线；格式由 `exploit/src/common/params_custom.h` 校验。

### dirtyfrag 链（按内核版本匹配）

dirtyfrag 链为每个 KMI 提供一个内核模块（App 资产中的 `dfr_lkm-<kmi>.ko`），
App 按设备内核选择对应模块。随包支持的 KMI：

| KMI            | 状态     | 备注                            |
| -------------- | -------- | ------------------------------- |
| android12-5.10 | 已测试   |                                 |
| android13-5.10 | 未测试   |                                 |
| android13-5.15 | 已测试   |                                 |
| android14-5.15 | 未测试   |                                 |
| android14-6.1  | 部分支持 | 仅 ≤6.1.71 内核；6.1.72+ 不支持 |
| android15-6.6  | 已测试   |                                 |
| android16-6.12 | 已测试   |                                 |
| android17-6.18 | 未测试   |                                 |

## 仓库结构

```
app/                             Android 管理端 App（Kotlin / Jetpack Compose）
  app/src/main/assets/payloads/  预构建 payload，随 APK 一起打包
exploit/
  src/common/                    共享层：日志、选项、参数表、KernelSnitch
                                 探测、KSU 协议、su_daemon、boot log、
                                 利用链骨架
  src/chains/pselect/            pselect 写链 + attr 载体 + UMH root 阶段
  src/chains/iosubmit/           io_submit 写链（zf8、One UI 9 的 s26）
  src/chains/dirtyfrag/          xfrm-ESP 页缓存存储安装链 + LKM 装载器
  src/series/s22|s26|zf8/        各系列的入口、参数表与汇编
```

## 构建

### App

所有 payload 已预构建于 `app/app/src/main/assets/payloads/` 下，APK 可独立
构建：

```bash
cd app
./gradlew assembleRelease
```

Gradle 任务 `:app:verifyPayloads` 会在构建时校验 payload 集合：每个系列目录
必须提供 `preload.so` 与 `su_daemon`，`payloads/ksud/` 必须提供 `ksud` 与
`ksud-next`，`payloads/dirtyfrag/` 必须提供 `dfr_payload` 及每个受支持 KMI
对应的一个 `dfr_lkm-<kmi>.ko`。

### 原生 payload

构建原生侧需要 Android NDK r30（或带 aarch64 sysroot 的宿主机 clang）。
`exploit/Makefile` 是唯一构建入口，产物落在 `exploit/build/`：

```bash
cd exploit
make            # 全量：各系列 + dirtyfrag payload
make s26        # 或单个目标：s26 / s22 / zf8 / dirtyfrag
```

### dirtyfrag 内核模块

该 LKM 是内核外部构建（out-of-tree kbuild）目标，需要一个准备好的内核构建
目录；Android DDK 容器会为每个 KMI 提供一份：

```bash
make lkm KDIR=/path/to/kernel-build-dir
```

### ksud 二进制

`payloads/ksud/ksud` 与 `payloads/ksud/ksud-next` 是预构建的 release 版本
（aarch64-linux-android），来自我们的 KernelSU fork：
[snothin/KernelSU](https://github.com/snothin/KernelSU) 与
[snothin/KernelSU-Next](https://github.com/snothin/KernelSU-Next)；版本升级
时从这两个仓库重新构建。

## 使用

1. 在受支持的设备上安装 APK（或按上文自行构建）。
2. 打开 USB 调试，通过 ADB 连接。
3. 在 App 内选择安装路径：
   - **dirtyfrag（推荐）**：单次运行——链直接完成 KernelSU 模块的部署与
     late-load。
   - **Ghostlock 链**：面向所选系列的竞争性利用；运行过程中设备会重启，
     之后由 App 执行 KernelSU late-load。

## 运行期开关

payload 读取少量由 App 经 ADB 注入的 `GHOSTSAM_*` 环境变量（例如
`GHOSTSAM_NO_KSU`、`GHOSTSAM_KSU_DEFER`、`GHOSTSAM_PARAMS_STRICT`）。权威
清单与语义以 payload 源码为准（`exploit/src/common/options.*`）；App 的 UI
仅暴露安全子集。

## 与 DFRoot 的关系

[DFRoot](https://github.com/diabl0w/DFRoot)（@diabl0w）是公开 DirtyFrag 技术
的专注、极简实现。GhostSam 把之前的工作（S22 / S26 / Z Fold8 的 Ghostlock
利用链）集成进同一个工具集，因此整个项目更大、也更为复杂——但 dirtyfrag
路径依然迅速：单次运行即完成部署与 late-load。欢迎体验，也欢迎提 Issue 或
PR。

两者基于同一公开技术；来源声明见 [NOTICE](NOTICE)。

## 免责声明

本仓库仅用于安全研究与教育，面向你拥有并控制的设备。运行 Ghostlock 链会
重启设备，会话期间 SELinux 可能处于 permissive（Knox watchdog 可能强制
重启）；强行指定错误的内核基线可能导致设备永久变砖。请自行对设备与当地
法律负责。

## 致谢与来源

- **s26 系列**：合并自 [ghostlock-s26](https://github.com/snothin/ghostlock-s26)，
  其 README 载有完整致谢清单。
- **Z Fold8 移植输入（io_submit primer）**：来自 @diabl0w。
- **ksud / ksud-next**：构建自 [snothin/KernelSU](https://github.com/snothin/KernelSU)
  与 [snothin/KernelSU-Next](https://github.com/snothin/KernelSU-Next)；这两个
  fork 跟踪上游并吸收 KernelSU 社区多位开发者的成果。
- **dirtyfrag 链**：沿袭公开的 DirtyFrag 技术；来源声明见 [NOTICE](NOTICE)。

## 许可

Apache-2.0，见 [`LICENSE`](LICENSE)。第三方声明与致谢见 [`NOTICE`](NOTICE)。
