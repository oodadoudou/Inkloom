#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>

// Kotlin Float expressions round after each operation. Do not fuse multiply/add here.
#pragma clang fp contract(off)

namespace {
float sample(const jbyte* alpha, int width, int height, float u, float v) {
    if (!(u >= 0.0f && u <= 1.0f && v >= 0.0f && v <= 1.0f)) return 0.0f;
    const float x = u * (width - 1), y = v * (height - 1);
    const int x0 = static_cast<int>(x), y0 = static_cast<int>(y);
    const int x1 = std::min(x0 + 1, width - 1), y1 = std::min(y0 + 1, height - 1);
    const float tx = x - x0, ty = y - y0;
    const float a = static_cast<std::uint8_t>(alpha[y0 * width + x0]);
    const float b = static_cast<std::uint8_t>(alpha[y0 * width + x1]);
    const float c = static_cast<std::uint8_t>(alpha[y1 * width + x0]);
    const float d = static_cast<std::uint8_t>(alpha[y1 * width + x1]);
    const float top = a + (b - a) * tx, bottom = c + (d - c) * tx;
    return (top + (bottom - top) * ty) * (1.0f / 255.0f);
}
bool valid_mask(JNIEnv* env, jbyteArray array, int width, int height) {
    return array != nullptr && width > 0 && height > 0 &&
        static_cast<std::int64_t>(width) * height <= env->GetArrayLength(array);
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeAccumulateStampCoverage(
    JNIEnv* env, jobject, jbyteArray destination_array,
    jint stride, jint tile_left, jint tile_top, jint left, jint top, jint right, jint bottom,
    jfloat center_x, jfloat center_y, jfloat radius_x, jfloat radius_y,
    jfloat cosine, jfloat sine, jfloat deposited_alpha,
    jbyteArray tip_array, jint tip_width, jint tip_height,
    jbyteArray grain_array, jint grain_width, jint grain_height,
    jfloat grain_density, jfloat grain_cosine, jfloat grain_sine,
    jfloat grain_wavelength, jfloat grain_movement, jfloat grain_phase_x, jfloat grain_phase_y) {
    if (destination_array == nullptr || stride <= 0 ||
        static_cast<std::int64_t>(stride) * stride > env->GetArrayLength(destination_array) ||
        left < tile_left || top < tile_top || right <= left || bottom <= top ||
        static_cast<std::int64_t>(right) - tile_left > stride ||
        static_cast<std::int64_t>(bottom) - tile_top > stride ||
        !(radius_x > 0.0f && radius_y > 0.0f) ||
        !valid_mask(env, tip_array, tip_width, tip_height) ||
        (grain_array != nullptr && (!valid_mask(env, grain_array, grain_width, grain_height) ||
            !(grain_wavelength > 0.0f)))) return JNI_FALSE;
    for (float value : {center_x, center_y, radius_x, radius_y, cosine, sine, deposited_alpha,
        grain_density, grain_cosine, grain_sine, grain_wavelength, grain_movement, grain_phase_x, grain_phase_y}) {
        if (!std::isfinite(value)) return JNI_FALSE;
    }
    // One bounded tile ROI only. Validation is complete before pinning; inside the critical
    // region there are no allocations, blocking operations or JNI calls except nested pin/release.
    // Avoid copying immutable tip/grain arrays again for every stamp that intersects a tile.
    jbyte* tip = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(tip_array, nullptr));
    if (tip == nullptr) return JNI_FALSE;
    jbyte* grain = grain_array == nullptr ? nullptr : static_cast<jbyte*>(env->GetPrimitiveArrayCritical(grain_array, nullptr));
    if (grain_array != nullptr && grain == nullptr) {
        env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
        return JNI_FALSE;
    }
    jbyte* destination = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(destination_array, nullptr));
    if (destination == nullptr) {
        if (grain != nullptr) env->ReleasePrimitiveArrayCritical(grain_array, grain, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
        return JNI_FALSE;
    }
    for (int y = top; y < bottom; ++y) for (int x = left; x < right; ++x) {
        const float px = x + 0.5f, py = y + 0.5f;
        const float dx = px - center_x, dy = py - center_y;
        const float local_x = cosine * dx + sine * dy;
        const float local_y = -sine * dx + cosine * dy;
        float coverage = sample(tip, tip_width, tip_height,
            (local_x / radius_x + 1.0f) * 0.5f,
            (local_y / radius_y + 1.0f) * 0.5f) * deposited_alpha;
        if (coverage <= 0.0f) continue;
        if (grain != nullptr) {
            const float gx = px - center_x * (1.0f - grain_movement);
            const float gy = py - center_y * (1.0f - grain_movement);
            const float rx = grain_cosine * gx + grain_sine * gy;
            const float ry = -grain_sine * gx + grain_cosine * gy;
            const float u = rx / grain_wavelength + grain_phase_x;
            const float v = ry / grain_wavelength + grain_phase_y;
            const float sampled = sample(grain, grain_width, grain_height,
                u - std::floor(u), v - std::floor(v));
            coverage *= 1.0f - grain_density + sampled * grain_density;
        }
        const int incoming = static_cast<int>(std::clamp(coverage, 0.0f, 1.0f) * 255.0f + 0.5f);
        if (incoming == 0) continue;
        const int index = (y - tile_top) * stride + x - tile_left;
        const int existing = static_cast<std::uint8_t>(destination[index]);
        destination[index] = static_cast<jbyte>(std::min(255, incoming + existing * (255 - incoming) / 255));
    }
    env->ReleasePrimitiveArrayCritical(destination_array, destination, 0);
    if (grain != nullptr) env->ReleasePrimitiveArrayCritical(grain_array, grain, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
    return JNI_TRUE;
}

namespace {
int combine_coverage(int primary, int secondary, int mode) {
    const float base = primary / 255.0f, blend = secondary / 255.0f;
    constexpr float epsilon = 1.0f / 65535.0f;
    float value;
    switch (mode) {
        case 0: value = blend + base * (1.0f - blend); break;
        case 1: value = base * blend; break;
        case 2: value = std::min(1.0f, base + blend); break;
        case 3: value = std::max(0.0f, base - blend); break;
        case 4: value = std::min(base, blend); break;
        case 5: value = std::max(base, blend); break;
        case 6: value = 1.0f - (1.0f - base) * (1.0f - blend); break;
        case 7: value = base <= 0.5f ? 2.0f * base * blend :
            1.0f - 2.0f * (1.0f - base) * (1.0f - blend); break;
        case 8: value = blend >= 1.0f - epsilon ? (base <= epsilon ? 0.0f : 1.0f) :
            std::min(1.0f, base / (1.0f - blend)); break;
        case 9: value = base >= 1.0f - epsilon ? 1.0f : (blend <= epsilon ? 0.0f :
            1.0f - std::min(1.0f, (1.0f - base) / blend)); break;
        case 10: value = std::max(0.0f, base + blend - 1.0f); break;
        case 11: value = base + blend >= 1.0f ? 1.0f : 0.0f; break;
        default: value = base * (1.0f - blend); break;
    }
    return static_cast<int>(std::clamp(value, 0.0f, 1.0f) * 255.0f + 0.5f);
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeCompositeDualCoverage(
    JNIEnv* env, jobject, jintArray destination_array, jbyteArray primary_array,
    jbyteArray secondary_array, jint stride, jint left, jint top, jint right, jint bottom,
    jint mode, jint color, jboolean erase) {
    const auto required = static_cast<std::int64_t>(stride) * stride;
    if (destination_array == nullptr || stride <= 0 || left < 0 || top < 0 ||
        right <= left || bottom <= top || right > stride || bottom > stride || mode < 0 || mode > 12 ||
        required > env->GetArrayLength(destination_array) ||
        (primary_array != nullptr && required > env->GetArrayLength(primary_array)) ||
        (secondary_array != nullptr && required > env->GetArrayLength(secondary_array))) return JNI_FALSE;
    jbyte* primary = primary_array == nullptr ? nullptr : static_cast<jbyte*>(env->GetPrimitiveArrayCritical(primary_array, nullptr));
    if (primary_array != nullptr && primary == nullptr) return JNI_FALSE;
    jbyte* secondary = secondary_array == nullptr ? nullptr : static_cast<jbyte*>(env->GetPrimitiveArrayCritical(secondary_array, nullptr));
    if (secondary_array != nullptr && secondary == nullptr) {
        if (primary != nullptr) env->ReleasePrimitiveArrayCritical(primary_array, primary, JNI_ABORT);
        return JNI_FALSE;
    }
    jint* destination = static_cast<jint*>(env->GetPrimitiveArrayCritical(destination_array, nullptr));
    if (destination == nullptr) {
        if (secondary != nullptr) env->ReleasePrimitiveArrayCritical(secondary_array, secondary, JNI_ABORT);
        if (primary != nullptr) env->ReleasePrimitiveArrayCritical(primary_array, primary, JNI_ABORT);
        return JNI_FALSE;
    }
    const auto rgb = static_cast<std::uint32_t>(color);
    for (int y = top; y < bottom; ++y) for (int x = left; x < right; ++x) {
        const int index = y * stride + x;
        const int combined = combine_coverage(primary == nullptr ? 0 : static_cast<std::uint8_t>(primary[index]),
            secondary == nullptr ? 0 : static_cast<std::uint8_t>(secondary[index]), mode);
        const float coverage = combined / 255.0f;
        const auto alpha = static_cast<std::uint32_t>((erase ? 255 : (rgb >> 24)) * coverage + 0.5f);
        destination[index] = static_cast<jint>(erase ?
            (alpha << 24) | (alpha << 16) | (alpha << 8) | alpha :
            (alpha << 24) | (((rgb >> 16 & 255) * alpha / 255) << 16) |
            (((rgb >> 8 & 255) * alpha / 255) << 8) | ((rgb & 255) * alpha / 255));
    }
    env->ReleasePrimitiveArrayCritical(destination_array, destination, 0);
    if (secondary != nullptr) env->ReleasePrimitiveArrayCritical(secondary_array, secondary, JNI_ABORT);
    if (primary != nullptr) env->ReleasePrimitiveArrayCritical(primary_array, primary, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeDepositUniformStamp(
    JNIEnv* env, jobject, jintArray destination_array,
    jint stride, jint tile_left, jint tile_top, jint left, jint top, jint right, jint bottom,
    jfloat center_x, jfloat center_y, jfloat radius_x, jfloat radius_y,
    jfloat cosine, jfloat sine, jfloat deposited_alpha,
    jbyteArray tip_array, jint tip_width, jint tip_height,
    jbyteArray grain_array, jint grain_width, jint grain_height,
    jfloat grain_density, jfloat grain_cosine, jfloat grain_sine,
    jfloat grain_wavelength, jfloat grain_movement, jfloat grain_phase_x, jfloat grain_phase_y, jint color) {
    if (destination_array == nullptr || stride <= 0 ||
        static_cast<std::int64_t>(stride) * stride > env->GetArrayLength(destination_array) ||
        left < tile_left || top < tile_top || right <= left || bottom <= top ||
        static_cast<std::int64_t>(right) - tile_left > stride ||
        static_cast<std::int64_t>(bottom) - tile_top > stride ||
        !(radius_x > 0.0f && radius_y > 0.0f) ||
        !valid_mask(env, tip_array, tip_width, tip_height) ||
        (grain_array != nullptr && (!valid_mask(env, grain_array, grain_width, grain_height) ||
            !(grain_wavelength > 0.0f)))) return JNI_FALSE;
    for (float value : {center_x, center_y, radius_x, radius_y, cosine, sine, deposited_alpha,
        grain_density, grain_cosine, grain_sine, grain_wavelength, grain_movement, grain_phase_x, grain_phase_y}) {
        if (!std::isfinite(value)) return JNI_FALSE;
    }
    jbyte* tip = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(tip_array, nullptr));
    if (tip == nullptr) return JNI_FALSE;
    jbyte* grain = grain_array == nullptr ? nullptr : static_cast<jbyte*>(env->GetPrimitiveArrayCritical(grain_array, nullptr));
    if (grain_array != nullptr && grain == nullptr) {
        env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
        return JNI_FALSE;
    }
    jint* destination = static_cast<jint*>(env->GetPrimitiveArrayCritical(destination_array, nullptr));
    if (destination == nullptr) {
        if (grain != nullptr) env->ReleasePrimitiveArrayCritical(grain_array, grain, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
        return JNI_FALSE;
    }
    for (int y = top; y < bottom; ++y) for (int x = left; x < right; ++x) {
        const float px = x + 0.5f, py = y + 0.5f;
        const float dx = px - center_x, dy = py - center_y;
        const float local_x = cosine * dx + sine * dy;
        const float local_y = -sine * dx + cosine * dy;
        float coverage = sample(tip, tip_width, tip_height,
            (local_x / radius_x + 1.0f) * 0.5f,
            (local_y / radius_y + 1.0f) * 0.5f) * deposited_alpha;
        if (coverage <= 0.0f) continue;
        if (grain != nullptr) {
            const float gx = px - center_x * (1.0f - grain_movement);
            const float gy = py - center_y * (1.0f - grain_movement);
            const float rx = grain_cosine * gx + grain_sine * gy;
            const float ry = -grain_sine * gx + grain_cosine * gy;
            const float u = rx / grain_wavelength + grain_phase_x;
            const float v = ry / grain_wavelength + grain_phase_y;
            const float sampled = sample(grain, grain_width, grain_height,
                u - std::floor(u), v - std::floor(v));
            coverage *= 1.0f - grain_density + sampled * grain_density;
        }
        coverage = std::clamp(1.0f - (1.0f - std::clamp(coverage, 0.0f, 1.0f)), 0.0f, 1.0f);
        const auto rgb = static_cast<std::uint32_t>(color);
        const int incoming = std::clamp(static_cast<int>((rgb >> 24) * coverage), 0, 255);
        if (incoming == 0) continue;
        const int index = (y - tile_top) * stride + x - tile_left;
        const auto old = static_cast<std::uint32_t>(destination[index]);
        const int inverse = 255 - incoming;
        const auto alpha = std::min(255u, incoming + (old >> 24) * inverse / 255);
        const auto red = std::min(255u, (rgb >> 16 & 255) * incoming / 255 + (old >> 16 & 255) * inverse / 255);
        const auto green = std::min(255u, (rgb >> 8 & 255) * incoming / 255 + (old >> 8 & 255) * inverse / 255);
        const auto blue = std::min(255u, (rgb & 255) * incoming / 255 + (old & 255) * inverse / 255);
        destination[index] = static_cast<jint>((alpha << 24) | (red << 16) | (green << 8) | blue);
    }
    env->ReleasePrimitiveArrayCritical(destination_array, destination, 0);
    if (grain != nullptr) env->ReleasePrimitiveArrayCritical(grain_array, grain, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
    return JNI_TRUE;
}
