# Inkloom 生产代码范围审计

核对：2026-09-23，`main` 的 R13 图像滤镜接入后。这里只记录当前建议；历史计划和生成的旧设计稿不参与完成判断。范围规则见 `AGENTS.md`，需求状态见 `CURRENT_REQUIREMENTS.md`。

| 代码范围 | 建议 | 当前调用与保留理由 |
|---|---|---|
| `engine/v5`、`brushpresetv5`、native raster | KEEP | 绘画、滤镜、笔刷导入和正式合成路径；不能因旧版本号删除 |
| `document` 与 Library／Pages／Templates | KEEP | 当前绘画和笔记数据模型及持久化所有者 |
| `ui/InkloomApp.kt`、`InkCanvasView.kt` | SIMPLIFY | 当前编辑器入口与输入所有者；可按真实领域拆分，不可直接删除 |
| `settings` 中 `showPageRail`／`showBrushPanel`／`showLayerPanel` | NEEDS_REVIEW | 面板树已存在，但这些字段仍用于初始化、保存、布局恢复和备份；移除前须做兼容迁移与重开回归 |
| `LegacyFillMigration.kt` 及对应测试 | KEEP | `InkloomViewModel` 打开旧笔记时调用；删除会使旧填充无法安全读取，且失败会显式保留原文档 |
| `BrushMasterSettingsStore` 的旧键回退 | KEEP | 老笔刷尺寸设置进入现有键空间的读取路径；需另做迁移后才能移除 |
| `CspShortcut` 历史默认绑定识别 | KEEP | 已保存快捷键升级到当前默认映射时需要；删除前须验证旧用户配置 |
| `WorkspaceLayoutProfiles`、布局备份字段 | NEEDS_REVIEW | 命名布局、文件导入导出、旧用户设置仍引用；需先做单一状态所有者迁移 |
| importer 与失败账本 | KEEP | 用户真实 SUT／ABR／Procreate 导入和显式失败回报 |
| `RasterLayerMergeEdit` 旧 `source`／`target`／`originalElements` 便捷属性 | DELETE | 无生产调用的源与元素别名已删除；`target` 旧检查会错误拒绝“可见副本”，改为按操作阶段验证来源或结果 ID |
| `RasterLayerMerge` 与 `DocumentAssetService` 的旧 Merge Down 便捷接口 | DELETE | 生产路径已使用 `Plan`／`renderLayerMergeSurface`；旧单层 ID 重载只剩旧测试引用，已迁走并删除 |
| 现有 OCR 与搜索 | KEEP | 已在笔记范围内，不扩展为 AI 产品 |
| 旧阶段 checklist、设计渲染脚本和参考截图 | DELETE | 已从当前文档入口移除；生成稿移至项目外可恢复归档 |

除已删除的合并便捷属性外，本次确认的旧生产代码均有实际读取或兼容责任，**没有更多可无条件删除的代码路径**。不以“名字含 Legacy”作为删除依据。下一轮若要消除兼容分支，应先写一次性迁移、验证旧笔记／旧设置／旧快捷键，再单独提交删除；不能用隐藏 UI 或空适配器代替清理。
