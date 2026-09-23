# 文档入口与清理规则

| 文件 | 职责 |
|---|---|
| [CURRENT_REQUIREMENTS.md](CURRENT_REQUIREMENTS.md) | 当前交付版本、宏观需求状态、用户优先级与真机验收边界 |
| [REQUESTED_FEATURE_CATALOG.md](REQUESTED_FEATURE_CATALOG.md) | 保留全部原编号的逐项检查清单；完整实现并有自动化证据才勾选 |
| [INTERACTION_CONTRACTS.md](INTERACTION_CONTRACTS.md) | 当前交互约束与官方行为参考，不单独作为完成证明 |
| [README.md](README.md) | 产品定位、构建与测试入口 |
| [AGENTS.md](AGENTS.md) | 工程、范围和交付规则 |
| [tasks/SCOPE_CLEANUP_AUDIT.md](tasks/SCOPE_CLEANUP_AUDIT.md) | 生产代码的保留／简化／删除依据；兼容迁移责任 |
| `dist/` 最新 Debug 证据 | APK 来源提交、版本、SHA-256、测试与设备限制 |

私有笔刷语料清单 `tasks/USER_BRUSH_CORPUS.md` 和用户暂缓的分屏设计 `tasks/DUAL_NOTEBOOK_SPLIT_VIEW.md` 留在本机，不上传素材。旧阶段 checklist、视觉渲染稿和旧状态矩阵不再放在当前文档入口；需要溯源时使用 Git 历史或项目外历史归档。

2026-09-20 已归档 186 份旧文档，校验清单在项目同级 `Inkloom-legacy-docs-20260920-230410/manifest.json`。2026-09-23 又将 926 个旧设计渲染文件／生成脚本（约 228 MB）移至项目同级 `Inkloom-legacy-generated-20260923/`，逐文件 SHA-256 见其 `manifest.json`；后续阶段 checklist 已合并到逐项需求目录。历史测试证据只证明对应版本，不能替代最新交付或 MovinkPad 验收。

后续只更新上述当前文件，不新增版本化“完成矩阵”。需要特定实现方案时，在 Git 提交说明或短期任务记录里关联 R 编号；交付后把完成状态归回当前状态表及细项清单。
