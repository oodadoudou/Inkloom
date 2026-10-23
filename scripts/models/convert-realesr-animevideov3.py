"""Rebuild app/src/main/assets/models/realesr-animevideov3.onnx from the official Real-ESRGAN weights.

Usage: python3 -I convert-realesr-animevideov3.py <realesr-animevideov3.pth> <output.onnx> <reference.onnx>

Weights: https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-animevideov3.pth
(SHA-256 b8a8376811077954d82ca3fcf476f1ac3da3e8a68a4f4d71363008000a18b75d), BSD-3-Clause, Xintao Wang.
Needs torch, onnx, onnxruntime and numpy. The reference model is any other export to compare against.
"""
import sys, torch, torch.nn as nn, torch.nn.functional as F, numpy as np, onnxruntime as ort
weights, output, reference = sys.argv[1], sys.argv[2], sys.argv[3]
class SRVGGNetCompact(nn.Module):
    def __init__(self, num_in_ch=3, num_out_ch=3, num_feat=64, num_conv=16, upscale=4):
        super().__init__()
        self.upscale = upscale
        self.body = nn.ModuleList()
        self.body.append(nn.Conv2d(num_in_ch, num_feat, 3, 1, 1))
        self.body.append(nn.PReLU(num_parameters=num_feat))
        for _ in range(num_conv):
            self.body.append(nn.Conv2d(num_feat, num_feat, 3, 1, 1))
            self.body.append(nn.PReLU(num_parameters=num_feat))
        self.body.append(nn.Conv2d(num_feat, num_out_ch * upscale * upscale, 3, 1, 1))
        self.upsampler = nn.PixelShuffle(upscale)
    def forward(self, x):
        out = x
        for layer in self.body: out = layer(out)
        out = self.upsampler(out)
        return out + F.interpolate(x, scale_factor=self.upscale, mode='nearest')
model = SRVGGNetCompact()
state = torch.load(weights, map_location='cpu', weights_only=True)
model.load_state_dict(state['params'] if 'params' in state else state, strict=True)
model.eval()
dummy = torch.rand(1, 3, 64, 64)
torch.onnx.export(model, dummy, output, opset_version=17, input_names=['input'], output_names=['output'],
    dynamic_axes={'input': {0: 'batch', 2: 'height', 3: 'width'}, 'output': {0: 'batch', 2: 'height_x4', 3: 'width_x4'}}, dynamo=False)
x = np.random.RandomState(7).rand(1, 3, 48, 40).astype(np.float32)
with torch.no_grad(): expected = model(torch.from_numpy(x)).numpy()
mine = ort.InferenceSession(output).run(None, {'input': x})[0]
theirs = ort.InferenceSession(reference).run(None, {'input': x})[0]
print('own export vs torch max abs', float(np.abs(mine - expected).max()))
print('hf model vs torch max abs', float(np.abs(theirs - expected).max()))
print('output shape', mine.shape)
