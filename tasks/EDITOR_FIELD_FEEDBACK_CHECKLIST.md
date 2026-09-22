# 2026-09-22 实机反馈验收清单

本清单只记录这一轮的复现与验收步骤；长期完成状态仍以 `CURRENT_REQUIREMENTS.md` 为准。每个编号对应一个可独立回退的修复提交。参考图：用户提供的 `465.png`（整页）和 `466.png`（页码跳转示意）。真机检查只有用户在 MovinkPad 上完成后才能标记 `DEVICE_VERIFIED`。

## A. 页面与笔记

- [x] **A01 页码快捷跳转**：长按右下角页码弹出可滚动的页面列表，点选后跳到对应页；整块长按可拖动，位置以画布相对坐标持久化，尺寸变化后保持在可见范围。Android 15 窗口测试覆盖三页跳转与拖动；横竖屏和实机触摸手感仍待用户验收。
- [x] **A02 切换笔记恢复进度**：从标题旁的快捷笔记列表切到 B，再切回 A，分别恢复各自最后一页；图库进入、进程重启、删掉原最后页后均有明确回退。保持各笔记独立记录。根因：快捷列表把目标笔记第一页作为显式内部链接传入，覆盖了恢复记录。Android 15 真实窗口测试在 B 的第 3 页切出并切回，通过。
- [x] **A03 Pages 顶部加页按钮**：页面列表滚到中段或底部时，“+”仍固定在面板顶部，点击继续按当前模板加页；长列表与窄面板不遮挡页面项。固定顶栏与滚动列表分层；Android 15 测试滚到第 8 页后仍可打开加页模板。

## B. 绘画质量与性能

- [ ] **B01 重开后笔迹模糊／像素边界**：对比新画、切页、关开笔记、重启进程的同一区域与缩放倍率；定位是持久化分辨率、tile 重采样、滤镜还是缓存，并修复根因。覆盖旧笔记与新笔刷，核对导出图与画布；不靠全局锐化掩盖问题。已定位并修正一个确定路径：旧 1200 px 像素层重开时投影到 1600 px 编辑栅格，tile 适配器先用最近邻放大，产生方块边。改为在原始尺寸裁切／旋转后使用过滤缩放；原始同尺寸像素仍逐点一致。Android 15 合成栅格回归通过；截图中所有模糊是否因此消除，仍待主设备对照。
- [ ] **B02 画布缩放卡顿**：采样双指缩放期间主线程、tile 解码／合成、帧耗时和内存；减少重复工作，保证缩放后笔尖对齐、绘画和撤销正常。已消除每次触点更新后 Compose 回写同一 viewport、背景和尺规状态时的重复失效请求，画笔宽度不变时也不再重复刷新悬停图标。Android 15 双指缩放后绘画回归需通过；120 Hz 真机帧耗时仍待测，暂不宣布性能解决。
- [ ] **B03 切换笔刷卡顿**：采样打开库、选择笔刷、首次落笔的耗时；缓存必要的笔尖／颗粒准备，避免 UI 同步解码；保持笔刷顺序、预览和画笔参数不变。已去掉选择时对整个导入笔刷库的同步 JSON 重新序列化；最近使用由独立的小型状态持久化，笔刷编辑仍单独保存。Android 15 窗口回归确认选中与历史写入，同时笔刷元数据保持原样。首次使用复杂笔尖的同步解码仍需实机耗时测量，不能仅凭此项宣布性能完全解决。参考 [Procreate 笔刷库](https://help.procreate.com/procreate/handbook/brushes/brush-library) 与 [CSP 子工具面板](https://help.clip-studio.com/en-us/manual_en/690_interface/Sub_Tool_palette.htm) 的选中／最近使用职责分离。

## C. 颜色与色板

- [x] **C01 当前主／副色写入**：色环、混色、色板、近似色及吸管选择颜色后，立即写入当前激活的前景色或背景色；切到另一页签不丢色，绘画取到同一颜色。根因之一是画布吸管始终提交到主色，现按激活色路由。Android 15 真窗口测试依次在色环改主色、近似色改副色、混色改主色、色板改副色、笔在画布上吸色改副色，并读取笔记级颜色存储核对另一色未被覆盖。参考 [CSP 选择颜色](https://help.clip-studio.com/en-us/manual_en/300_color/Selecting_colors.htm) 和 [Procreate 颜色界面](https://help.procreate.com/procreate/handbook/colors/colors-interface)。
- [x] **C02 色块旁换色**：主／副色交换按钮移至两个色块旁，保留 32 dp 触摸热区；托管色环原本传入空回调的六点缩放柄已移除。Android 15 真窗口测试核对按钮位置、无效控件消失，以及交换后持久化主／副色互换。参考 [CSP 色环](https://help.clip-studio.com/en-us/manual_en/300_color/Color_Wheel_palette.htm) 和 [Procreate 颜色界面](https://help.procreate.com/procreate/handbook/colors/colors-interface)。
- [x] **C03 色板导入模式**：文件和图片导入前均选择“追加颜色”或“替换颜色”，显示目标色板并允许取消；打开系统选择器时锁定目标 ID。解析失败、空色板或目标被删除时保持旧色板；追加保留原顺序与名字，替换保留目标色板身份、替换其颜色内容。JVM 测试覆盖目标隔离和失败不变，Android 15 窗口测试覆盖文件／图片两种导入入口。参考 [CSP 色板管理](https://help.clip-studio.com/en-us/manual_en/300_color/Color_Set_palette.htm) 和 [Procreate 色板导入](https://help.procreate.com/procreate/handbook/colors/colors-palettes)。

## D. 面板与笔刷控制

- [x] **D01 标签滑动不误拖**：多标签组合现在仅从每个标签左侧的移动抓手拆出；标签文字区域保留点选和横向滚动。Android 15 面板交互测试覆盖横向滑动不拆组、滚动后从抓手拆出、拖动取消和停靠回退；真机多指中断触感仍待验收。参考 [CSP 面板标签](https://help.clip-studio.com/en-us/manual_en/690_interface/Palettes.htm) 和 [Photoshop 移动面板](https://helpx.adobe.com/photoshop/desktop/get-started/learn-the-basics/move-panels.html)。
- [x] **D02 笔刷面板窄宽阈值**：完整分类＋笔刷列表保留至约 196 dp（分类 56、分隔 24、预览约 112）；顶部操作栏超宽时横向滚动，分类名称省略仍可见。Android 15 真窗口测试把笔刷窗缩到约 205 dp，确认分栏仍存在；JVM 测试固定阈值，窗口宽度仍由既有布局持久化。主设备反复缩窄／停靠手感待验收。参考 [CSP 面板](https://help.clip-studio.com/en-us/manual_en/690_interface/Palettes.htm) 和 [Photoshop 面板尺寸](https://helpx.adobe.com/photoshop/desktop/get-started/learn-the-basics/stack-floating-panels.html)。
- [x] **D03 笔刷大小／透明度微调**：两条侧边进度条右侧各有小号 ±；每次点击大小变化 1 px、透明度变化 1 个百分点，并限制在 0.5–400 px／5–100%。Android 15 窗口测试核对笔刷引擎实际宽度、不透明度以及最大值不越界；长按连调并非这次用户明确要求，维持逐次精调。参考 [Procreate 侧边栏](https://help.procreate.com/procreate/handbook/brushes/paint-smudge-erase) 与 [CSP 工具滑块](https://help.clip-studio.com/en-us/manual_en/240_brushes/Tool_Slider_palette.htm)。
- [x] **D04 笔刷记忆标记**：在大小或透明度滑块上停住长按，添加该位置的记忆标记；同一标记再次长按即移除。每支笔刷、每种控制、绘画／涂抹／橡皮角色各自保存，最多四个；普通拖动靠近标记会吸附，横向移开手指可绕过吸附。Android 15 窗口测试验证添加四个、第五个不覆盖、吸附、移除、切笔隔离、大小／透明度隔离、重开保留及橡皮角色隔离；真机长按手感仍待验收。参考 [Procreate 大小与透明度记忆标记](https://help.procreate.com/procreate/handbook/brushes/paint-smudge-erase) 与 [CSP 笔刷大小预设](https://help.clip-studio.com/en-us/manual_en/240_brushes/Customizing_the_brush_size_palette.htm)。

## 每项提交与最终交付

- 每项修复单独提交并推送 `main`，提交说明写明复现与验收证据；任何未通过验证的项保持未勾选。
- 相关 JVM 与 Android 交互／栅格测试、Android 15 冒烟、lint、`git diff --check` 全通过后再打最新 Debug APK；仅保留该包，核对源码 commit 与 SHA-256，按仓库脚本同步 Google Drive Desktop。
- 每次提交检查周额度；剩余接近 50% 时停止新项，完成进行中的修复、验证和交付。实机触感与 120 Hz 性能保持 `DEVICE_PENDING`，直到用户实测。

参考行为：[Procreate 大小与透明度记忆标记](https://help.procreate.com/procreate/handbook/brushes/paint-smudge-erase)、[Procreate 侧边栏精调手势](https://help.procreate.com/procreate/handbook/interface-gestures/interface)、[CSP 面板停靠](https://help.clip-studio.com/en-us/manual_en/690_interface/Palettes.htm)。
A01 页面导航参考：[Goodnotes 页面侧栏](https://support.goodnotes.com/hc/en-us/articles/9497798035983-Use-the-Sidebar-to-navigate-your-document)、[CSP 页面管理器](https://help.clip-studio.com/en-us/manual_en/570_pages/Page_Manager.htm)。选择用页码入口打开独立可滚动列表，并把可拖控件约束在画布可见区域。
