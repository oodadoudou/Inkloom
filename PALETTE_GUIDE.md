# 漫画、插画、线稿与草稿色板

筛选日期：2026-09-12。按人物、上色、插画氛围、线稿和草稿工作流整理。
这里是资源候选与导入计划，不表示这些第三方色板已经内置在 APK。

## 优先试用的现成色板

下载量只用于判断使用基础，不等于质量排名；同一素材的旧版和新版不混算。下面“用途”是结合作者介绍给出的选用建议。

| 用途 | 原始资源与作者 | 页面证据 | 选用建议 |
|---|---|---|---|
| 人物肤色 | [Skin color color set / 肌色カラーセット](https://assets.clip-studio.com/en-us/detail?id=1358861)，CELSYS 官方 | 免费；约 47 万下载、1.14 万赞；135 色 | 漫画人物基础肤色、从亮部到阴影的阶梯 |
| 柔和插画 | [Pastel color set](https://assets.clip-studio.com/en-us/detail?id=1714000)，健太郎 | 免费；约 2.7 万下载、921 赞；此链接为修正版 | 浅色服装、日常场景和柔和配色；线稿另配深色 |
| 明亮插画 | [Soft Visual Novel](https://assets.clip-studio.com/en-us/detail?id=1798080)，saturns_day | 免费；约 2 万下载；作者说明包含多种明度、偏浅色 | 人物与场景、马克笔和彩铅风格；保留作者原排列 |
| 复古插画 | [Antique color set](https://assets.clip-studio.com/en-us/detail?id=1753993)，t.HC/Chi | 免费；搜索索引约 2.3 万下载、1116 赞，详情页暂未重新打开 | 褪色感、童话感插画；最终取用前再核对详情页 |
| 漫画配色 | [Comic Comics 375 Color Palette](https://assets.clip-studio.com/en-us/detail?id=1705371)，Barrel_The_Ape | 免费；约 2.1 万下载；作者标注 Comico / DC Comic 配色 | 偏传统美漫的上色参考，不当作日系漫画唯一默认色板 |
| 线稿＋草稿 | [Choose line art colors / 선화색 골라먹기](https://assets.clip-studio.com/en-us/detail?id=2283290)，suhyenan | 搜索索引显示免费；作者说明增加两种草稿色和一种线稿色；详情页暂不可读取 | 需求匹配候选，尚不能称为高人气精选或已验证资源 |
| Procreate 插画配色 | [Lisa Bardot 免费色板库](https://bardotbrush.com/procreate-color-palettes/) | 作者官网直接提供 `.swatches`；未公布可比下载量 | Rosy Days、In the Garden、Calm Autumn、Neutral Puppies 等小组配色先看作者预览选择 |

## Inkloom 内的分组要求

- 线稿：纯黑、黑灰、暖棕、深蓝、深紫；颜色本身与笔刷不透明度分开。
- 草稿：浅蓝、浅红、淡紫、浅灰；用于区分构图、修正和正式线稿。
- 人物：肤色的底色、阴影、深阴影、高光相邻；头发和服装另组。
- 插画：柔和、暖色、冷色、复古、夜景各组；保留常用中性色。
- 第三方色板保留原名、色序和颜色名称，不根据点击次数自动重排。
- 基础线稿/草稿若由 Inkloom 原创配制，应明确为 Inkloom 基础色，不冒充下载自 CSP 或艺术家的原版。

## 导入与来源规则

当前 0.191 开发源码只有自建、增删改、复制和排序色板，没有外部色板文件导入。补齐导入前，资源仍在上述原始页面获取。

- [CSP 官方手册](https://help.clip-studio.com/en-us/manual_en/300_color/Color_Set_palette.htm)支持 `.cls` 与 Photoshop `.aco`，ASSETS 素材可添加到色板。
- [Procreate 官方手册](https://help.procreate.com/procreate/handbook/colors/colors-palettes)说明色板管理和 `.swatches` 导入；Bardot 页面提供实际文件，可作为获授权个人使用的解析样本。
- [CSP 素材使用规则](https://support.clip-studio.com/en-us/faq/articles/20190003)允许个人/商业作品使用及导出，禁止把下载素材转给其他用户。
- [Bardot 授权](https://bardotbrush.com/license/)允许创作使用，禁止转发下载内容。下载文件仅作为用户本机素材，不提交 GitHub、不直接打入公开 APK。

## 验收清单

- [x] 漫画/插画资源候选；原作者、原链接、用途和热度证据。
- [x] 线稿、草稿色纳入需求和分组。
- [x] 核对 CSP、Procreate 文件导入行为及资源使用范围。
- [ ] 添加原创基础线稿/草稿色板；允许选择添加、编辑和删除，不覆盖已有色板。
- [ ] 实际文件导入：名称、顺序、重复颜色、透明度、编码、颜色空间。
- [ ] 各格式真实文件对照；未知版本/颜色空间明确提示，不悄悄丢色。
- [ ] 从文件选择器导入、取消、损坏文件、重名、重复导入、应用重启测试。
- [ ] 选择颜色后主/副色正确；切回混色、关闭重开仍保留当前色板。
- [ ] MovinkPad 用户实测。
