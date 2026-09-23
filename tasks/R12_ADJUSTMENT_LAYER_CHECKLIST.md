# R12 调整图层实施与验收清单（2026-09-23）

本文件只跟踪这一轮实现步骤；长期状态以 `CURRENT_REQUIREMENTS.md` 为准。每项独立提交且保持可编译。实机仍按 `DEVICE_PENDING` 处理。

## 交互依据与决定

- [CSP 色调校正](https://help.clip-studio.com/en-us/manual_en/390_filters/Tonal_Correction.htm)：新建校正图层作用于同组中下方图层；调整顺序会改变作用范围，Normal 文件夹限制作用范围、Through 文件夹可影响外部。点缩略图重新编辑，蒙版可绘制且选区可初始化蒙版。
- [Photoshop 调整图层](https://helpx.adobe.com/photoshop/desktop/create-manage-layers/color-adjustment-fill-layers/create-adjustment-layers.html)：从图层面板创建，在属性界面修改参数，不改写下方原始像素。
- [Photoshop 调整蒙版](https://helpx.adobe.com/photoshop/desktop/create-manage-layers/color-adjustment-fill-layers/use-layer-masks-to-target-adjustment-or-fill-layers.html)：蒙版的白／灰／黑对应完全／部分／不应用；修改蒙版后仍可重调参数。
- Inkloom 合同：在当前层上方创建调整层；点调整缩略图进入参数编辑。预览为暂态，取消完全恢复，应用形成一项撤销。普通绘画接触在调整层上明确拒绝；蒙版仍可编辑。持久化、关开笔记和导出使用同一合成顺序。已有六种操作按实际能力呈现，不放空菜单。

## 实施顺序

- [x] **R12a 合成接线**：画布与页面导出把调整节点送入正式投影；单层／Normal 文件夹／Through 作用范围、透明度和蒙版一致。绘画中的 active delta 沿用原投影输入。Android 15 像素测试比较蒙版、上下层、两种文件夹模式的实时画布和导出，均通过。
- [x] **R12b 安全创建与选择**：图层面板可创建六种调整层，正确插入当前层上方或当前组内；复制、整组复制／删除、重做保留调整参数。选中后画笔明确拒绝画到参数层，蒙版仍可绘制。Android 窗口测试覆盖创建、蒙版与 Activity 重建，JVM 覆盖序列化。
- [x] **R12c 参数面板**：六种参数均有实际控件；滑动暂态预览，取消恢复，应用只产生一项撤销。点调整缩略图可继续修改。调整层透明度与混合模式进入画布、导出同一合成链。逐像素变换使用复用缓冲，不再每像素构建参数列表。
- [ ] **R12d 回归闭环**：JVM 像素及序列化、Android 真窗口交互／蒙版／撤销／重开／导出，完整 Android 15、lint、diff check；打包最新 Debug 并与源码 SHA 对齐后同步 Drive Desktop。

## 已知边界

专业 ICC／CMYK 归 R11；图像级模糊、锐化等归 R13。调整层蒙版可以独立编辑、移动与复制；但“应用蒙版并烘焙”在调整层上目前隐藏，因为仅烘焙该参数层会丢失下方颜色，不能假装成功。要永久烘焙可将调整层与受影响的下方图层一起合并。普通像素层的直接破坏性调色不是调整图层的替代品。缺少 MovinkPad 实测时不得宣称 `DEVICE_VERIFIED`。
