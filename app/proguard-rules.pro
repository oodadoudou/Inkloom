# Inkloom release shrinking rules. Library rules (AndroidX, Compose, ML Kit, kotlinx.serialization)
# ship with the libraries; the defaults keep classes and names that have native methods.

# ONNX Runtime's native code looks its Java classes up by name.
-keep class ai.onnxruntime.** { *; }

# Stack traces in bug reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
