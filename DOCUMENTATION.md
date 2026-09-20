# 文档入口与清理规则

当前有效文档：

| 文件 | 职责 |
|---|---|
| [CURRENT_REQUIREMENTS.md](CURRENT_REQUIREMENTS.md) | 唯一当前需求状态、用户 Priority、开发与验收区别 |
| [REQUESTED_FEATURE_CATALOG.md](REQUESTED_FEATURE_CATALOG.md) | 保留此前请求的细项编号；不保存完成状态或优先级 |
| [INTERACTION_CONTRACTS.md](INTERACTION_CONTRACTS.md) | 当前交互合同及官方参考；不是完成证明 |
| [README.md](README.md) | 产品定位、构建与测试命令 |
| [AGENTS.md](AGENTS.md) | 工程、范围与交付规则 |
| `dist/` 最新 Debug 证据 | 已交付源码、版本、SHA-256、测试结果与设备限制 |
| 本机 `tasks/USER_BRUSH_CORPUS.md` | 私有真实笔刷语料清单，不提交素材 |
| 本机 `tasks/DUAL_NOTEBOOK_SPLIT_VIEW.md` | 用户明确暂缓的分屏设计，仅后续恢复时使用 |
| 本机 `tasks/SCOPE_CLEANUP_AUDIT.md` | 当前代码范围审计，历史日志不再混入 |

## 2026-09-20 清理

归档并从工作目录移除 **186 份旧文档，共 34,365,117 字节**：6 份受 Git 管理的旧计划／合同，及本机旧矩阵、阶段报告、旧架构设计、Word／PDF 产品设计版本和旧视觉方案。归档位于项目的同级 `Inkloom-legacy-docs-20260920-230410/`，每个文件都已校验 ZIP 内容与原文件 SHA-256 一致，清单位于其中的 `manifest.json`。Git 管理的旧文件也可从 `f9ec495` 及以前历史找回。

保留了生产源码、未应用的 stash、真实笔刷和测试 fixture、第三方源码文档／授权与来源说明、构建缓存、当前 APK 与有用的测试日志／栅格证据。历史验证证据只能证明其对应版本；不能把 `.codex-artifacts/` 内的旧结果当作当前需求状态。

修正的主要歧义：

- 已完成的编组／解组／整组复制删除及布局组合不再出现在“完全未开发”栏。
- 原生图片＋像素混合变换已有，但当前会栅格化；保留各对象结构仍缺失。
- 图片纸张已完成基础工作流，不能继续按早期“仅 proposal”判断。
- 完整合并、蒙版烘焙仍缺，未交付 stash 不计入已实现。
- 旧白色主题、旧 Profile 交付要求、历次 50%／6% 等额度阈值不再进入当前产品计划。
- 旧清单的 252 个 CSP 功能／验收条目和原矩阵 75 个能力目标保留为细项索引，没有把未核对项删掉来缩小目标。

后续变更同步更新唯一状态表；历史记录放 Git 提交和对应交付证据，不再新增 `CURRENT_GAPS_版本`、`最终完成`、`新 matrix` 等相互冲突的状态文件。需要更细的实现方案时，从当前 R 编号引用，并明确其是否已交付。

本次仅文档修改：核对内部链接、条目数量、归档哈希、`git diff --check`；不重复运行 Android 构建或生成内容相同的 APK。
