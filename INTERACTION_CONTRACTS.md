# Inkloom 当前交互合同

这是实现约束与参考索引；完成状态只见 [CURRENT_REQUIREMENTS.md](CURRENT_REQUIREMENTS.md)。参考产品行为需要在下一次实现前复核，以下链接沿用此前研究，不表示本轮重新核查了官方网页。

## 工作区

- 深灰背景、中性黑白控件、原创图标。未获用户要求不更换为蓝色选中主题。
- 面板只有显示和关闭；X 关闭当前标签，重新打开保留尺寸、位置和内容状态。用户不需要缩成小模块、仅图标模式或停靠方向菜单。
- 标题移动面板组，标签移动单个面板；边缘接近和抓取位置决定停靠候选，不以大面积重叠自动吸附。释放时提交，取消恢复原布局，拖动只能显示一份内容。
- 布局支持边缘拆分、标签组合、标签排序、命名保存恢复；工具条位置纳入布局。
- 面板标题移动、尺寸调整、列表滚动、内容拖动不能争抢接触，也不能在画布留下误笔。
- 色环与混色可以同时打开；来回选色不会关闭混色或丢失混色会话。默认数值模式 RGB。

## 图层与蒙版

- 固定混合模式／透明度条，紧凑命令条；眼睛只改可见性，组箭头只折叠，层名选层，内容与蒙版缩略图分别切换目标。主活动层与辅助多选明确区分，不用一支笔作为选中标志。
- 透明度实时预览、松手一条历史、取消恢复；组透明度作用于组的合成结果。
- 删除整组同时删子树；解组保留子层。当前解组遇到不能保持外观的组效果要明确拒绝，不能静默改变成像。
- 普通有色笔显示 alpha 蒙版，橡皮／透明模式隐藏；黑色本身不等于隐藏。
- 每次结构操作只形成一次可撤销事务，资源引用覆盖撤销寿命。受保护内容明确拒绝，不悄悄切去另一绘画层。
- 完整合并和蒙版烘焙尚未闭合，不能根据菜单／枚举存在宣布完成。

## 选区与变换

- 已选套索时，非手掌单指接触仍属于选区，即便紧接手写笔之后；第二根手指接管导航。切换输入方式不得串入孤立的上一接触点。
- 渐变像素限制在选区内；选区外保护叠色是显示反馈，不是渐变漏绘。
- 图片＋像素有可准备的混合源时允许共同变换；当前应用会栅格化对象，取消／撤销需恢复对象和像素。没有可准备的源时明确拒绝，不能只变换像素导致部分提交。此合同替代旧的“所有保留对象一律不支持变换”。
- 变换始终从冻结源预览，确认一次提交、取消原样恢复；多次拖动不累计重采样。

## 笔刷与笔记

- 最近与置顶独立；选中普通分类中的笔刷不自动移到列表顶部。长按拖动用于排序／分组，编辑使用明确入口。
- 文件夹导入保留嵌套分组；导入失败要说明到文件／成员及原因。高级近似语义不可称为原引擎 1:1 保真。
- 纸张模板与封面分别管理；预设与用户保存分组。图片纸张是背景资产，不是可擦除绘画图层；原图移动后本地资产仍应可用。
- 导入预览允许取消、设名称和宽高、保留比例、完整显示／裁切及底色；取消不能误建笔记。追加页继承当前页模板。
- 删除模板库条目不删除已有笔记仍引用的资产。

## 核心官方参考

- [CSP 图层](https://help.clip-studio.com/en-us/manual_en/180_layers/Using_layers.htm)、[文件夹](https://help.clip-studio.com/en-us/manual_en/180_layers/Layer_folders.htm)、[蒙版](https://help.clip-studio.com/en-us/manual_en/180_layers/Layer_masks.htm)。
- [Photoshop 图层组合并](https://helpx.adobe.com/photoshop/using/layers.html)、[面板组合](https://helpx.adobe.com/photoshop/desktop/get-started/learn-the-basics/manipulate-panel-groups.html)。
- [CSP 工作区](https://help.clip-studio.com/en-us/manual_en/690_interface/Register_and_manage_your_workspace.htm)、[工具与子工具](https://help.clip-studio.com/en-us/manual_en/150_tools/Customizing_the_Tool_and_Sub_Tool_palettes.htm)。
- [CSP 选区](https://help.clip-studio.com/en-us/manual_en/330_selection/Selection_area.htm)、[触摸手势](https://help.clip-studio.com/en-us/manual_en/750_gestures/Basic_touch_gestures.htm)、[Photoshop 套索](https://helpx.adobe.com/photoshop/using/selecting-lasso-tools.html)。
- [CSP 色板](https://help.clip-studio.com/en-us/manual_en/300_color/Color_Set_palette.htm)、[Procreate 色板](https://help.procreate.com/procreate/handbook/colors/colors-palettes)。
- [Goodnotes 模板](https://support.goodnotes.com/hc/en-us/articles/7353728093199-Import-and-manage-custom-notebook-templates)、[Procreate 新建画布](https://help.procreate.com/procreate/handbook/gallery/gallery-create)。

第三方笔刷／色板仅用于用户授权的本地使用和测试，授权不明或禁止再分发的资源不提交 GitHub、不打进 APK。完整旧研究链接与素材候选保存在项目外归档内。

## 面板 layout 修复合同（2026-09-20）

本轮按用户最新指示只完成 layout，笔刷保真暂缓，图层合并在途代码单独保存。
重新核查 [CSP 面板宽高与停靠](https://help.clip-studio.com/en-us/manual_en/690_interface/Palettes.htm) 与 [Photoshop 浮动面板尺寸](https://helpx.adobe.com/photoshop/desktop/get-started/learn-the-basics/stack-floating-panels.html)。

- 浮窗四边调单轴、四角调双轴；拖动固定相对边，达到最小尺寸／屏幕边缘后不漂移。命中区域加宽，视觉仍是中性单线。
- 停靠组靠画布侧及组间分隔线可连续调节；左右停靠可调各面板高度，上下停靠可调宽度。独占一列／行时允许缩短面板并保留空白停靠区，不强制占满整条边。
- 从按下时的几何与根坐标计算拖动，不从正在移动的边缘累加局部位移。预览不写盘，松手一次保存；系统取消、第二指、Escape／返回与屏幕旋转取消并恢复。
- 关闭重开／进程重启／命名布局保留尺寸；键盘／窄屏仅暂时约束可见范围，不覆盖保存的大尺寸。锁定布局阻止拖动、缩放、拆分、标签排序与工具条移位，面板开关／内容操作仍可用。
- 不新增缩成图标、最小化或方向停靠菜单；仍以边缘拖放决定位置。

验收：浮窗四边四角；左／右／上／下停靠；嵌套分隔线；面板内容滚动无误触；取消恢复；锁定；命名布局与重启；绘画／悬停／橡皮／切笔／缩放后绘画回归。模拟器通过后仍标记 DEVICE_PENDING。

布局持久化补全（2026-09-21）：当前停靠树、标签顺序、尺寸、打开状态、锁定与命名布局统一进入资料库备份；命名布局保留工具栏方向和位置。恢复时先校验整个布局，再更新同一个在用状态对象；损坏布局明确拒绝，不能悄悄清空已有布局。旧备份缺少布局时保留当前布局，不从旧字段伪造停靠树。

边缘热区不越过浮窗侧边去覆盖画布工具；标题上边缘不占用标题中央的拖动路径。实际窗口外框和边缘手柄热区分别测量，屏幕边缘的手柄内移不能改变持久化尺寸。

### 窄面板、组合停靠与选区浮条补充合同（2026-09-21）

用户实机发现 0.203.0 的内容最低宽度仍阻止面板继续收窄，而且边缘抓取、上下组合和选区工具条会互相遮挡。重新核对 [CSP Palettes](https://help.clip-studio.com/en-us/manual_en/690_interface/Palettes.htm)、[Photoshop panels](https://helpx.adobe.com/photoshop/desktop/get-started/learn-the-basics/dock-undock-panels.html)、[Procreate Selections](https://help.procreate.com/procreate/handbook/selections/selections-interface) 与 [Photoshop Elements Contextual Task Bar](https://helpx.adobe.com/ca/photoshop-elements/desktop/workspace-and-environment/contextual-task-bar.html)。

- 面板容器最小宽高只保证标题、拖动和恢复能力；内容的理想宽度不能反向阻止用户缩窄。低于各内容可用阈值时自动进入紧凑标签栏，重新拉宽即恢复内容，不新增需要用户维护的手动折叠状态。
- 浮窗四条完整边均可长按后拖动；角落同时改变两个维度。短按仍交给面板内容，长按开始后由稳定根节点持有手势，取消恢复按下前几何。
- 拖到任意 Block 的标题形成标签组；拖到 Block 的上／下／左／右边缘形成分割。边缘命中使用可见的整边指示线，不要求命中一个孤立点。嵌套分割、标签组、拆离和保存恢复使用同一布局树。
- 选区工具条是画布上的上下文浮条：使用明确抓手拖动，临时视口只约束渲染位置，松手保存，旋转／大字体后仍保持可达。它不能固定覆盖右下翻页或已停靠面板；拖动不改变选区像素或模式。

### 双指轻点撤销防误触合同（2026-09-21）

重新核对 [Procreate Gestures](https://help.procreate.com/procreate/handbook/interface-gestures/gestures) 与 [CSP Basic touch gestures](https://help.clip-studio.com/en-us/manual_en/750_gestures/Basic_touch_gestures.htm)：两者都把撤销定义为两指轻点，并把双指拖动、捏合与旋转定义为画布导航。Inkloom 因此只在两根手指于 120ms 内相继落下、整次手势不超过 220ms、每根手指都保持在系统触摸阈值的 45% 内，且没有缩放或旋转时撤销一次。任何超阈值移动立即永久转为导航，抬手时不能再回退成撤销。
