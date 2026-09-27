# Inkloom 0.217.0 Debug

Source: `1c8b3f65bb309f7df81a19adf758aaf547e33d91`

- 组合面板：从任意子面板的组抓手移动完整浮窗或停靠组合；只有单个标签旁的抓手会将其拆出。单标签文字不再被整块拖离，横向滑动用于浏览标签。
- 轻微误拖在松手时还原布局；取消手势恢复原状。拖动目标及标签插入提示沿用现有深灰工作区。
- 官方交互参考：Photoshop 面板组与 Clip Studio Paint 调色板；采用 Inkloom 自有图标与实现。
- 验证：JVM 953 项（0 失败、8 跳过）；Android 15 全量 756 项（0 失败、0 跳过）；lint、diff、v2 签名、安装和冷启动通过。
- MovinkPad 真机拖动手感为 `DEVICE_PENDING`；Drive Desktop 本地同步副本 SHA-256 已校验，云端下载未验证。
