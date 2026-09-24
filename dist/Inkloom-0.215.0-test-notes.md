# Inkloom 0.215.0 Debug

Source: `0a9902d5e167a0bd42cbb05fb3c16e1abd3caabc`

- 基础工具：高级填充、命名渐变预设及文件交换、共用画笔笔尖的擦除／混色、液化畸变和惯性。
- 验证：934 项 JVM 测试（0 失败、8 跳过）；Android 15 基础工具、真实笔刷样本、局部帧回归、lint、diff、v2 签名、安装与冷启动通过。
- 全量 Android 740 项未通过：颜色窗口测试在前 159 项中出现 6 次失败，随后停止；重启模拟器单独重跑仍超时。未把该检查计为通过。
- MovinkPad 真机触感为 `DEVICE_PENDING`；Google Drive Desktop 的本地副本校验不代表远端云下载。
