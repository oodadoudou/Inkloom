#include <jni.h>

#include <algorithm>
#include <bit>
#include <cmath>
#include <cstdint>
#include <type_traits>
#include <vector>

namespace {

constexpr float kAlphaEpsilon = 1.0e-7f;
bool supported_mode(int mode) { return (mode >= 0 && mode <= 13) || (mode >= 18 && mode <= 27); }

struct PremultipliedColor {
    float red;
    float green;
    float blue;
    float alpha;
};

float finite_or_zero(float value) { return std::isfinite(value) ? value : 0.0f; }
float clamp01(float value) { return std::clamp(finite_or_zero(value), 0.0f, 1.0f); }

float straight(float premultiplied, float alpha) {
    return alpha <= kAlphaEpsilon ? 0.0f : finite_or_zero(premultiplied) / alpha;
}

float hard_light(float backdrop, float source) {
    return source <= 0.5f ? 2.0f * backdrop * source :
        backdrop + 2.0f * source - 1.0f - 2.0f * backdrop * source;
}

float soft_light(float backdrop_value, float source_value) {
    const float backdrop = clamp01(backdrop_value);
    const float source = clamp01(source_value);
    if (source <= 0.5f) {
        return backdrop - (1.0f - 2.0f * source) * backdrop * (1.0f - backdrop);
    }
    const float d = backdrop <= 0.25f ?
        ((16.0f * backdrop - 12.0f) * backdrop + 4.0f) * backdrop : std::sqrt(backdrop);
    return backdrop + (2.0f * source - 1.0f) * (d - backdrop);
}

float blend_channel(float backdrop, float source, int mode) {
    switch (mode) {
        case 0: return source;
        case 1: return std::min(backdrop, source);
        case 2: return backdrop * source;
        case 3: {
            const float b = clamp01(backdrop);
            const float s = clamp01(source);
            return s <= 0.0f ? 0.0f : 1.0f - std::min(1.0f, (1.0f - b) / s);
        }
        case 4: return backdrop + source - 1.0f;
        case 5: return std::max(backdrop, source);
        case 6: return backdrop + source - backdrop * source;
        case 7: {
            const float b = clamp01(backdrop);
            const float s = clamp01(source);
            return s >= 1.0f ? 1.0f : std::min(1.0f, b / (1.0f - s));
        }
        case 8: return backdrop + source;
        case 9: return hard_light(source, backdrop);
        case 10: return soft_light(backdrop, source);
        case 11: return hard_light(backdrop, source);
        case 12: return std::abs(backdrop - source);
        case 13: return backdrop + source - 2.0f * backdrop * source;
        case 18: return backdrop - source;
        case 19: return backdrop <= 0.0f ? 0.0f : source >= 1.0f ? 1.0f : std::min(65504.0f, backdrop / std::max(kAlphaEpsilon, 1.0f - source));
        case 20: return backdrop + source;
        case 21: return source < 0.5f ? blend_channel(backdrop, 2.0f * source, 3) : blend_channel(backdrop, 2.0f * source - 1.0f, 7);
        case 22: return backdrop + 2.0f * source - 1.0f;
        case 23: return source < 0.5f ? std::min(backdrop, 2.0f * source) : std::max(backdrop, 2.0f * source - 1.0f);
        case 24: return backdrop + source < 1.0f ? 0.0f : 1.0f;
        case 27: return backdrop <= 0.0f ? 0.0f : std::min(1.0f, backdrop / std::max(kAlphaEpsilon, source));
        default: return source;
    }
}

PremultipliedColor composite(
    const PremultipliedColor& source,
    const PremultipliedColor& destination,
    int mode,
    float opacity, bool bounded = false) {
    const float layer_opacity = clamp01(opacity);
    const float raw_source_alpha = clamp01(source.alpha);
    const float source_alpha = raw_source_alpha * layer_opacity;
    const float destination_alpha = clamp01(destination.alpha);
    if (source_alpha <= kAlphaEpsilon) return destination;

    const float source_red = straight(source.red, raw_source_alpha);
    const float source_green = straight(source.green, raw_source_alpha);
    const float source_blue = straight(source.blue, raw_source_alpha);
    const float destination_red = straight(destination.red, destination_alpha);
    const float destination_green = straight(destination.green, destination_alpha);
    const float destination_blue = straight(destination.blue, destination_alpha);
    const float source_lum = 0.3f * source_red + 0.59f * source_green + 0.11f * source_blue;
    const float dest_lum = 0.3f * destination_red + 0.59f * destination_green + 0.11f * destination_blue;
    const bool source_wins = mode == 25 ? source_lum < dest_lum : source_lum > dest_lum;
    auto blend = [&](float b, float s) {
        float result = mode == 25 || mode == 26 ? (source_wins ? s : b) : blend_channel(b, s, mode);
        return bounded && mode != 19 && mode != 20 ? clamp01(result) : result;
    };
    const float source_only = 1.0f - destination_alpha;
    const float destination_only = 1.0f - source_alpha;
    const float overlap = source_alpha * destination_alpha;
    return {
        finite_or_zero(source.red) * layer_opacity * source_only +
            finite_or_zero(destination.red) * destination_only +
            blend(destination_red, source_red) * overlap,
        finite_or_zero(source.green) * layer_opacity * source_only +
            finite_or_zero(destination.green) * destination_only +
            blend(destination_green, source_green) * overlap,
        finite_or_zero(source.blue) * layer_opacity * source_only +
            finite_or_zero(destination.blue) * destination_only +
            blend(destination_blue, source_blue) * overlap,
        std::clamp(source_alpha + destination_alpha * (1.0f - source_alpha), 0.0f, 1.0f),
    };
}

std::uint8_t unit_byte(float value) {
    return static_cast<std::uint8_t>(std::lround(clamp01(value) * 255.0f));
}

PremultipliedColor unpack_rgba8(std::uint32_t pixel) {
    return {
        static_cast<float>((pixel >> 16) & 0xffu) / 255.0f,
        static_cast<float>((pixel >> 8) & 0xffu) / 255.0f,
        static_cast<float>(pixel & 0xffu) / 255.0f,
        static_cast<float>((pixel >> 24) & 0xffu) / 255.0f,
    };
}

std::uint32_t pack_rgba8(const PremultipliedColor& color) {
    const std::uint8_t alpha = unit_byte(color.alpha);
    if (alpha == 0) return 0;
    const float alpha_float = static_cast<float>(alpha) / 255.0f;
    return (static_cast<std::uint32_t>(alpha) << 24) |
        (static_cast<std::uint32_t>(unit_byte(std::clamp(color.red, 0.0f, alpha_float))) << 16) |
        (static_cast<std::uint32_t>(unit_byte(std::clamp(color.green, 0.0f, alpha_float))) << 8) |
        static_cast<std::uint32_t>(unit_byte(std::clamp(color.blue, 0.0f, alpha_float)));
}

std::uint32_t divide_255(std::uint32_t value) {
    return (value + 127u) / 255u;
}

std::uint32_t composite_rgba8_fast(std::uint32_t source, std::uint32_t destination, int mode, float opacity) {
    const float layer_opacity = clamp01(opacity);
    const std::uint32_t raw_source_alpha = (source >> 24) & 0xffu;
    const std::uint32_t source_alpha = static_cast<std::uint32_t>(
        std::lround(static_cast<float>(raw_source_alpha) * layer_opacity));
    if (source_alpha == 0) return destination;
    const std::uint32_t destination_alpha = (destination >> 24) & 0xffu;
    const std::uint32_t inverse_source_alpha = 255u - source_alpha;
    const std::uint32_t inverse_destination_alpha = 255u - destination_alpha;
    const std::uint32_t output_alpha = std::min(
        255u,
        source_alpha + divide_255(destination_alpha * inverse_source_alpha));
    auto channel = [&](int shift) {
        const std::uint32_t source_channel = static_cast<std::uint32_t>(std::lround(
            static_cast<float>((source >> shift) & 0xffu) * layer_opacity));
        const std::uint32_t destination_channel = (destination >> shift) & 0xffu;
        const std::uint32_t value = mode == 0 ?
            source_channel + divide_255(destination_channel * inverse_source_alpha) :
            divide_255(source_channel * inverse_destination_alpha) +
                divide_255(destination_channel * inverse_source_alpha) +
                divide_255(source_channel * destination_channel);
        return std::min(value, output_alpha);
    };
    return (output_alpha << 24) | (channel(16) << 16) | (channel(8) << 8) | channel(0);
}

float half_to_float(std::uint16_t half) {
    const std::uint32_t sign = static_cast<std::uint32_t>(half & 0x8000u) << 16;
    int exponent = (half >> 10) & 0x1f;
    std::uint32_t mantissa = half & 0x03ffu;
    std::uint32_t bits;
    if (exponent == 0) {
        if (mantissa == 0) {
            bits = sign;
        } else {
            exponent = 1;
            while ((mantissa & 0x0400u) == 0) {
                mantissa <<= 1;
                --exponent;
            }
            mantissa &= 0x03ffu;
            bits = sign | (static_cast<std::uint32_t>(exponent + 127 - 15) << 23) | (mantissa << 13);
        }
    } else if (exponent == 0x1f) {
        bits = sign | 0x7f800000u | (mantissa << 13);
    } else {
        bits = sign | (static_cast<std::uint32_t>(exponent + 127 - 15) << 23) | (mantissa << 13);
    }
    return std::bit_cast<float>(bits);
}

std::uint16_t float_to_half(float value) {
    const std::uint32_t bits = std::bit_cast<std::uint32_t>(value);
    const std::uint32_t sign = (bits >> 16) & 0x8000u;
    const std::uint32_t exponent = (bits >> 23) & 0xffu;
    std::uint32_t mantissa = bits & 0x7fffffu;
    if (exponent == 0xffu) return static_cast<std::uint16_t>(sign | (mantissa == 0 ? 0x7c00u : 0x7e00u));
    int half_exponent = static_cast<int>(exponent) - 127 + 15;
    if (half_exponent >= 0x1f) return static_cast<std::uint16_t>(sign | 0x7c00u);
    if (half_exponent <= 0) {
        if (half_exponent < -10) return static_cast<std::uint16_t>(sign);
        mantissa |= 0x800000u;
        const int shift = 14 - half_exponent;
        const std::uint32_t rounded =
            (mantissa + (1u << (shift - 1)) - 1u + ((mantissa >> shift) & 1u)) >> shift;
        return static_cast<std::uint16_t>(sign | rounded);
    }
    mantissa += 0x0fffu + ((mantissa >> 13) & 1u);
    if ((mantissa & 0x800000u) != 0) {
        mantissa = 0;
        ++half_exponent;
        if (half_exponent >= 0x1f) return static_cast<std::uint16_t>(sign | 0x7c00u);
    }
    return static_cast<std::uint16_t>(sign | (static_cast<std::uint32_t>(half_exponent) << 10) |
        (mantissa >> 13));
}

PremultipliedColor unpack_f16(std::uint64_t pixel) {
    return {
        half_to_float(static_cast<std::uint16_t>(pixel & 0xffffu)),
        half_to_float(static_cast<std::uint16_t>((pixel >> 16) & 0xffffu)),
        half_to_float(static_cast<std::uint16_t>((pixel >> 32) & 0xffffu)),
        half_to_float(static_cast<std::uint16_t>((pixel >> 48) & 0xffffu)),
    };
}

std::uint64_t pack_f16(const PremultipliedColor& color) {
    return static_cast<std::uint64_t>(float_to_half(finite_or_zero(color.red))) |
        (static_cast<std::uint64_t>(float_to_half(finite_or_zero(color.green))) << 16) |
        (static_cast<std::uint64_t>(float_to_half(finite_or_zero(color.blue))) << 32) |
        (static_cast<std::uint64_t>(float_to_half(clamp01(color.alpha))) << 48);
}

bool valid_request(
    JNIEnv* env,
    jarray source,
    jarray destination,
    int stride,
    int left,
    int top,
    int right,
    int bottom,
    int mode) {
    if (source == nullptr || destination == nullptr || env->IsSameObject(source, destination) ||
        stride <= 0 || left < 0 || top < 0 || right <= left || bottom <= top || right > stride ||
        !supported_mode(mode)) return false;
    const jsize source_size = env->GetArrayLength(source);
    const jsize destination_size = env->GetArrayLength(destination);
    const std::int64_t required = static_cast<std::int64_t>(bottom) * stride;
    return required <= source_size && required <= destination_size;
}

template<typename JArray, typename Element, typename Unpack, typename Pack>
jboolean composite_array(
    JNIEnv* env,
    JArray source_array,
    JArray destination_array,
    int stride,
    int left,
    int top,
    int right,
    int bottom,
    int mode,
    float opacity,
    Unpack unpack,
    Pack pack) {
    if (!valid_request(env, source_array, destination_array, stride, left, top, right, bottom, mode)) {
        return JNI_FALSE;
    }
    Element* source;
    if constexpr (std::is_same_v<JArray, jintArray>) {
        source = reinterpret_cast<Element*>(env->GetIntArrayElements(source_array, nullptr));
    } else {
        source = reinterpret_cast<Element*>(env->GetLongArrayElements(source_array, nullptr));
    }
    if (source == nullptr) return JNI_FALSE;
    Element* destination;
    if constexpr (std::is_same_v<JArray, jintArray>) {
        destination = reinterpret_cast<Element*>(env->GetIntArrayElements(destination_array, nullptr));
    } else {
        destination = reinterpret_cast<Element*>(env->GetLongArrayElements(destination_array, nullptr));
    }
    if (destination == nullptr) {
        if constexpr (std::is_same_v<JArray, jintArray>) {
            env->ReleaseIntArrayElements(source_array, reinterpret_cast<jint*>(source), JNI_ABORT);
        } else {
            env->ReleaseLongArrayElements(source_array, reinterpret_cast<jlong*>(source), JNI_ABORT);
        }
        return JNI_FALSE;
    }
    for (int y = top; y < bottom; ++y) {
        int index = y * stride + left;
        for (int x = left; x < right; ++x, ++index) {
            const auto source_color = unpack(static_cast<std::make_unsigned_t<Element>>(source[index]));
            if (source_color.alpha <= 0.0f) continue;
            const auto destination_color = unpack(static_cast<std::make_unsigned_t<Element>>(destination[index]));
            destination[index] = static_cast<Element>(pack(composite(source_color, destination_color, mode, opacity, std::is_same_v<Element, jint>)));
        }
    }
    if constexpr (std::is_same_v<JArray, jintArray>) {
        env->ReleaseIntArrayElements(destination_array, reinterpret_cast<jint*>(destination), 0);
        env->ReleaseIntArrayElements(source_array, reinterpret_cast<jint*>(source), JNI_ABORT);
    } else {
        env->ReleaseLongArrayElements(destination_array, reinterpret_cast<jlong*>(destination), 0);
        env->ReleaseLongArrayElements(source_array, reinterpret_cast<jlong*>(source), JNI_ABORT);
    }
    return JNI_TRUE;
}

template<typename Element, typename Unpack, typename Pack>
void composite_flat_layers(
    const Element* sources,
    int source_layer_stride,
    int layer_count,
    Element* destination,
    int stride,
    int left,
    int top,
    int right,
    int bottom,
    const jint* modes,
    const jfloat* opacities,
    Unpack unpack,
    Pack pack) {
    for (int layer = 0; layer < layer_count; ++layer) {
        const auto* source = sources + static_cast<std::size_t>(layer) * source_layer_stride;
        const int mode = modes[layer];
        const float opacity = opacities[layer];
        for (int y = top; y < bottom; ++y) {
            int index = y * stride + left;
            for (int x = left; x < right; ++x, ++index) {
                const auto source_color = unpack(static_cast<std::make_unsigned_t<Element>>(source[index]));
                if (source_color.alpha <= 0.0f) continue;
                const auto destination_color =
                    unpack(static_cast<std::make_unsigned_t<Element>>(destination[index]));
                destination[index] = static_cast<Element>(
                    pack(composite(source_color, destination_color, mode, opacity, std::is_same_v<Element, jint>)));
            }
        }
    }
}

void composite_flat_rgba8_layers(
    const jint* sources,
    int source_layer_stride,
    int layer_count,
    jint* destination,
    int stride,
    int left,
    int top,
    int right,
    int bottom,
    const jint* modes,
    const jfloat* opacities) {
    for (int layer = 0; layer < layer_count; ++layer) {
        const auto* source = sources + static_cast<std::size_t>(layer) * source_layer_stride;
        const int mode = modes[layer];
        const float opacity = opacities[layer];
        for (int y = top; y < bottom; ++y) {
            int index = y * stride + left;
            for (int x = left; x < right; ++x, ++index) {
                const std::uint32_t source_pixel = static_cast<std::uint32_t>(source[index]);
                if ((source_pixel >> 24) == 0) continue;
                if (mode == 0 || mode == 2) {
                    destination[index] = static_cast<jint>(composite_rgba8_fast(
                        source_pixel,
                        static_cast<std::uint32_t>(destination[index]),
                        mode,
                        opacity));
                } else {
                    destination[index] = static_cast<jint>(pack_rgba8(composite(
                        unpack_rgba8(source_pixel),
                        unpack_rgba8(static_cast<std::uint32_t>(destination[index])),
                        mode,
                        opacity, true)));
                }
            }
        }
    }
}

template<typename Element, typename Unpack, typename Pack>
void composite_flat_layers_with_coverage(
    const Element* sources,
    const jbyte* coverages,
    int source_layer_stride,
    int layer_count,
    Element* destination,
    int stride,
    int left,
    int top,
    int right,
    int bottom,
    const jint* modes,
    const jfloat* opacities,
    Unpack unpack,
    Pack pack) {
    for (int layer = 0; layer < layer_count; ++layer) {
        const auto* source = sources + static_cast<std::size_t>(layer) * source_layer_stride;
        const auto* coverage = coverages + static_cast<std::size_t>(layer) * source_layer_stride;
        const int mode = modes[layer];
        const float opacity = opacities[layer];
        for (int y = top; y < bottom; ++y) {
            int index = y * stride + left;
            for (int x = left; x < right; ++x, ++index) {
                const auto source_color = unpack(static_cast<std::make_unsigned_t<Element>>(source[index]));
                const float pixel_opacity = opacity *
                    (static_cast<float>(static_cast<std::uint8_t>(coverage[index])) / 255.0f);
                if (source_color.alpha <= 0.0f || pixel_opacity <= 0.0f) continue;
                const auto destination_color =
                    unpack(static_cast<std::make_unsigned_t<Element>>(destination[index]));
                destination[index] = static_cast<Element>(
                    pack(composite(source_color, destination_color, mode, pixel_opacity, std::is_same_v<Element, jint>)));
            }
        }
    }
}

void composite_flat_rgba8_layers_with_coverage(
    const jint* sources,
    const jbyte* coverages,
    int source_layer_stride,
    int layer_count,
    jint* destination,
    int stride,
    int left,
    int top,
    int right,
    int bottom,
    const jint* modes,
    const jfloat* opacities) {
    for (int layer = 0; layer < layer_count; ++layer) {
        const auto* source = sources + static_cast<std::size_t>(layer) * source_layer_stride;
        const auto* coverage = coverages + static_cast<std::size_t>(layer) * source_layer_stride;
        const int mode = modes[layer];
        const float opacity = opacities[layer];
        for (int y = top; y < bottom; ++y) {
            int index = y * stride + left;
            for (int x = left; x < right; ++x, ++index) {
                const std::uint32_t source_pixel = static_cast<std::uint32_t>(source[index]);
                const float pixel_opacity = opacity *
                    (static_cast<float>(static_cast<std::uint8_t>(coverage[index])) / 255.0f);
                if ((source_pixel >> 24) == 0 || pixel_opacity <= 0.0f) continue;
                if (mode == 0 || mode == 2) {
                    destination[index] = static_cast<jint>(composite_rgba8_fast(
                        source_pixel,
                        static_cast<std::uint32_t>(destination[index]),
                        mode,
                        pixel_opacity));
                } else {
                    destination[index] = static_cast<jint>(pack_rgba8(composite(
                        unpack_rgba8(source_pixel),
                        unpack_rgba8(static_cast<std::uint32_t>(destination[index])),
                        mode,
                        pixel_opacity, true)));
                }
            }
        }
    }
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_composite_NativeCompositeOps_nativeCompositeRgba8(
    JNIEnv* env,
    jobject,
    jintArray source,
    jintArray destination,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jint mode,
    jfloat opacity) {
    return composite_array<jintArray, jint>(
        env, source, destination, stride, left, top, right, bottom, mode, opacity,
        unpack_rgba8, pack_rgba8);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_composite_NativeCompositeOps_nativeCompositeF16(
    JNIEnv* env,
    jobject,
    jlongArray source,
    jlongArray destination,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jint mode,
    jfloat opacity) {
    return composite_array<jlongArray, jlong>(
        env, source, destination, stride, left, top, right, bottom, mode, opacity,
        unpack_f16, pack_f16);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_composite_NativeCompositeOps_nativeCompositeRgba8Layers(
    JNIEnv* env,
    jobject,
    jintArray sources,
    jint source_layer_stride,
    jint layer_count,
    jintArray destination,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jintArray modes,
    jfloatArray opacities) {
    if (sources == nullptr || destination == nullptr || modes == nullptr || opacities == nullptr ||
        source_layer_stride <= 0 || layer_count <= 0 || stride <= 0 || left < 0 || top < 0 ||
        right <= left || bottom <= top || right > stride || env->GetArrayLength(modes) != layer_count ||
        env->GetArrayLength(opacities) != layer_count ||
        static_cast<std::int64_t>(source_layer_stride) * layer_count > env->GetArrayLength(sources) ||
        static_cast<std::int64_t>(bottom) * stride > env->GetArrayLength(destination)) return JNI_FALSE;
    std::vector<jint> mode_values(static_cast<std::size_t>(layer_count));
    std::vector<jfloat> opacity_values(static_cast<std::size_t>(layer_count));
    env->GetIntArrayRegion(modes, 0, layer_count, mode_values.data());
    env->GetFloatArrayRegion(opacities, 0, layer_count, opacity_values.data());
    if (env->ExceptionCheck() ||
        std::any_of(mode_values.begin(), mode_values.end(), [](jint mode) { return !supported_mode(mode); })) {
        return JNI_FALSE;
    }
    auto* source_values = env->GetIntArrayElements(sources, nullptr);
    if (source_values == nullptr) return JNI_FALSE;
    auto* destination_values = env->GetIntArrayElements(destination, nullptr);
    if (destination_values == nullptr) {
        env->ReleaseIntArrayElements(sources, source_values, JNI_ABORT);
        return JNI_FALSE;
    }
    composite_flat_rgba8_layers(
        source_values, source_layer_stride, layer_count, destination_values,
        stride, left, top, right, bottom, mode_values.data(), opacity_values.data());
    env->ReleaseIntArrayElements(destination, destination_values, 0);
    env->ReleaseIntArrayElements(sources, source_values, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_composite_NativeCompositeOps_nativeCompositeF16Layers(
    JNIEnv* env,
    jobject,
    jlongArray sources,
    jint source_layer_stride,
    jint layer_count,
    jlongArray destination,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jintArray modes,
    jfloatArray opacities) {
    if (sources == nullptr || destination == nullptr || modes == nullptr || opacities == nullptr ||
        source_layer_stride <= 0 || layer_count <= 0 || stride <= 0 || left < 0 || top < 0 ||
        right <= left || bottom <= top || right > stride || env->GetArrayLength(modes) != layer_count ||
        env->GetArrayLength(opacities) != layer_count ||
        static_cast<std::int64_t>(source_layer_stride) * layer_count > env->GetArrayLength(sources) ||
        static_cast<std::int64_t>(bottom) * stride > env->GetArrayLength(destination)) return JNI_FALSE;
    std::vector<jint> mode_values(static_cast<std::size_t>(layer_count));
    std::vector<jfloat> opacity_values(static_cast<std::size_t>(layer_count));
    env->GetIntArrayRegion(modes, 0, layer_count, mode_values.data());
    env->GetFloatArrayRegion(opacities, 0, layer_count, opacity_values.data());
    if (env->ExceptionCheck() ||
        std::any_of(mode_values.begin(), mode_values.end(), [](jint mode) { return !supported_mode(mode); })) {
        return JNI_FALSE;
    }
    auto* source_values = env->GetLongArrayElements(sources, nullptr);
    if (source_values == nullptr) return JNI_FALSE;
    auto* destination_values = env->GetLongArrayElements(destination, nullptr);
    if (destination_values == nullptr) {
        env->ReleaseLongArrayElements(sources, source_values, JNI_ABORT);
        return JNI_FALSE;
    }
    composite_flat_layers(
        source_values, source_layer_stride, layer_count, destination_values,
        stride, left, top, right, bottom, mode_values.data(), opacity_values.data(),
        unpack_f16, pack_f16);
    env->ReleaseLongArrayElements(destination, destination_values, 0);
    env->ReleaseLongArrayElements(sources, source_values, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_composite_NativeCompositeOps_nativeCompositeRgba8LayersWithCoverage(
    JNIEnv* env,
    jobject,
    jintArray sources,
    jbyteArray coverage,
    jint source_layer_stride,
    jint layer_count,
    jintArray destination,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jintArray modes,
    jfloatArray opacities) {
    if (sources == nullptr || coverage == nullptr || destination == nullptr || modes == nullptr || opacities == nullptr ||
        source_layer_stride <= 0 || layer_count <= 0 || stride <= 0 || left < 0 || top < 0 ||
        right <= left || bottom <= top || right > stride || env->GetArrayLength(modes) != layer_count ||
        env->GetArrayLength(opacities) != layer_count ||
        static_cast<std::int64_t>(source_layer_stride) * layer_count > env->GetArrayLength(sources) ||
        static_cast<std::int64_t>(source_layer_stride) * layer_count > env->GetArrayLength(coverage) ||
        static_cast<std::int64_t>(bottom) * stride > env->GetArrayLength(destination)) return JNI_FALSE;
    std::vector<jint> mode_values(static_cast<std::size_t>(layer_count));
    std::vector<jfloat> opacity_values(static_cast<std::size_t>(layer_count));
    env->GetIntArrayRegion(modes, 0, layer_count, mode_values.data());
    env->GetFloatArrayRegion(opacities, 0, layer_count, opacity_values.data());
    if (env->ExceptionCheck() ||
        std::any_of(mode_values.begin(), mode_values.end(), [](jint mode) { return !supported_mode(mode); })) return JNI_FALSE;
    auto* source_values = env->GetIntArrayElements(sources, nullptr);
    auto* coverage_values = env->GetByteArrayElements(coverage, nullptr);
    auto* destination_values = env->GetIntArrayElements(destination, nullptr);
    if (source_values == nullptr || coverage_values == nullptr || destination_values == nullptr) {
        if (destination_values != nullptr) env->ReleaseIntArrayElements(destination, destination_values, 0);
        if (coverage_values != nullptr) env->ReleaseByteArrayElements(coverage, coverage_values, JNI_ABORT);
        if (source_values != nullptr) env->ReleaseIntArrayElements(sources, source_values, JNI_ABORT);
        return JNI_FALSE;
    }
    composite_flat_rgba8_layers_with_coverage(
        source_values, coverage_values, source_layer_stride, layer_count, destination_values,
        stride, left, top, right, bottom, mode_values.data(), opacity_values.data());
    env->ReleaseIntArrayElements(destination, destination_values, 0);
    env->ReleaseByteArrayElements(coverage, coverage_values, JNI_ABORT);
    env->ReleaseIntArrayElements(sources, source_values, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_composite_NativeCompositeOps_nativeCompositeF16LayersWithCoverage(
    JNIEnv* env,
    jobject,
    jlongArray sources,
    jbyteArray coverage,
    jint source_layer_stride,
    jint layer_count,
    jlongArray destination,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jintArray modes,
    jfloatArray opacities) {
    if (sources == nullptr || coverage == nullptr || destination == nullptr || modes == nullptr || opacities == nullptr ||
        source_layer_stride <= 0 || layer_count <= 0 || stride <= 0 || left < 0 || top < 0 ||
        right <= left || bottom <= top || right > stride || env->GetArrayLength(modes) != layer_count ||
        env->GetArrayLength(opacities) != layer_count ||
        static_cast<std::int64_t>(source_layer_stride) * layer_count > env->GetArrayLength(sources) ||
        static_cast<std::int64_t>(source_layer_stride) * layer_count > env->GetArrayLength(coverage) ||
        static_cast<std::int64_t>(bottom) * stride > env->GetArrayLength(destination)) return JNI_FALSE;
    std::vector<jint> mode_values(static_cast<std::size_t>(layer_count));
    std::vector<jfloat> opacity_values(static_cast<std::size_t>(layer_count));
    env->GetIntArrayRegion(modes, 0, layer_count, mode_values.data());
    env->GetFloatArrayRegion(opacities, 0, layer_count, opacity_values.data());
    if (env->ExceptionCheck() ||
        std::any_of(mode_values.begin(), mode_values.end(), [](jint mode) { return !supported_mode(mode); })) return JNI_FALSE;
    auto* source_values = env->GetLongArrayElements(sources, nullptr);
    auto* coverage_values = env->GetByteArrayElements(coverage, nullptr);
    auto* destination_values = env->GetLongArrayElements(destination, nullptr);
    if (source_values == nullptr || coverage_values == nullptr || destination_values == nullptr) {
        if (destination_values != nullptr) env->ReleaseLongArrayElements(destination, destination_values, 0);
        if (coverage_values != nullptr) env->ReleaseByteArrayElements(coverage, coverage_values, JNI_ABORT);
        if (source_values != nullptr) env->ReleaseLongArrayElements(sources, source_values, JNI_ABORT);
        return JNI_FALSE;
    }
    composite_flat_layers_with_coverage(
        source_values, coverage_values, source_layer_stride, layer_count, destination_values,
        stride, left, top, right, bottom, mode_values.data(), opacity_values.data(),
        unpack_f16, pack_f16);
    env->ReleaseLongArrayElements(destination, destination_values, 0);
    env->ReleaseByteArrayElements(coverage, coverage_values, JNI_ABORT);
    env->ReleaseLongArrayElements(sources, source_values, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_composite_NativeCompositeOps_nativeMultiplyCoverageByRgba8Alpha(
    JNIEnv* env,
    jobject,
    jbyteArray coverage_array,
    jint coverage_offset,
    jintArray pixels_array,
    jint stride,
    jint width,
    jint height,
    jint opacity_byte) {
    if (coverage_array == nullptr || pixels_array == nullptr || coverage_offset < 0 ||
        stride <= 0 || width <= 0 || height <= 0 || width > stride || opacity_byte < 0 ||
        opacity_byte > 255) return JNI_FALSE;
    const jsize coverage_size = env->GetArrayLength(coverage_array);
    const jsize pixels_size = env->GetArrayLength(pixels_array);
    const std::int64_t required = static_cast<std::int64_t>(height - 1) * stride + width;
    if (coverage_offset + required > coverage_size || required > pixels_size) return JNI_FALSE;
    auto* coverage = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(coverage_array, nullptr));
    if (coverage == nullptr) return JNI_FALSE;
    auto* pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(pixels_array, nullptr));
    if (pixels == nullptr) {
        env->ReleasePrimitiveArrayCritical(coverage_array, coverage, JNI_ABORT);
        return JNI_FALSE;
    }
    for (int y = 0; y < height; ++y) {
        int pixel_index = y * stride;
        int coverage_index = coverage_offset + pixel_index;
        for (int x = 0; x < width; ++x, ++pixel_index, ++coverage_index) {
            const int current = static_cast<std::uint8_t>(coverage[coverage_index]);
            const int alpha = static_cast<std::uint32_t>(pixels[pixel_index]) >> 24;
            const int masked = (current * alpha + 127) / 255;
            coverage[coverage_index] = static_cast<jbyte>((masked * opacity_byte + 127) / 255);
        }
    }
    env->ReleasePrimitiveArrayCritical(pixels_array, pixels, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(coverage_array, coverage, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeAffineSelectionTile(
    JNIEnv* env, jobject, jobject source_array, jbyteArray source_alpha_array, jint source_left, jint source_top,
    jint source_width, jint source_height, jobject destination_array, jbyteArray destination_alpha_array,
    jint tile_left, jint tile_top, jint stride, jint width, jint height, jfloatArray inverse_array, jint interpolation, jboolean high_precision) {
#pragma clang fp contract(off)
    if (!source_array || !source_alpha_array || !destination_array || !destination_alpha_array || !inverse_array ||
        source_width <= 0 || source_height <= 0 || width <= 0 || height <= 0 || stride < width || interpolation < 0 || interpolation > 2 ||
        (env->GetArrayLength(inverse_array) != 6 && env->GetArrayLength(inverse_array) != 9)) return JNI_FALSE;
    const auto source_count = static_cast<std::int64_t>(source_width) * source_height;
    const auto destination_count = static_cast<std::int64_t>(stride) * height;
    if (source_count > env->GetArrayLength(static_cast<jarray>(source_array)) || source_count > env->GetArrayLength(source_alpha_array) ||
        destination_count > env->GetArrayLength(static_cast<jarray>(destination_array)) ||
        static_cast<std::int64_t>(width) * height > env->GetArrayLength(destination_alpha_array)) return JNI_FALSE;
    float m[9] = {0,0,0,0,0,0,0,0,1}; env->GetFloatArrayRegion(inverse_array, 0, env->GetArrayLength(inverse_array), m);
    void* source = env->GetPrimitiveArrayCritical(static_cast<jarray>(source_array), nullptr);
    if (!source) return JNI_FALSE;
    void* destination = env->GetPrimitiveArrayCritical(static_cast<jarray>(destination_array), nullptr);
    if (!destination) { env->ReleasePrimitiveArrayCritical(static_cast<jarray>(source_array), source, JNI_ABORT); return JNI_FALSE; }
    auto* source_alpha = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(source_alpha_array, nullptr));
    auto* destination_alpha = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(destination_alpha_array, nullptr));
    if (!source_alpha || !destination_alpha) {
        if (source_alpha) env->ReleasePrimitiveArrayCritical(source_alpha_array, source_alpha, JNI_ABORT);
        if (destination_alpha) env->ReleasePrimitiveArrayCritical(destination_alpha_array, destination_alpha, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(static_cast<jarray>(source_array), source, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(static_cast<jarray>(destination_array), destination, JNI_ABORT);
        return JNI_FALSE;
    }
    auto cubic = [](float v) { const float x = std::abs(v); return x < 1 ? 1.5f*x*x*x - 2.5f*x*x + 1 : x < 2 ? -.5f*x*x*x + 2.5f*x*x - 4*x + 2 : 0; };
    auto byte_scale = [](std::uint32_t value, int alpha) { return (value * alpha + 127) / 255; };
    auto at = [&](int x, int y) -> PremultipliedColor {
        if (x < 0 || y < 0 || x >= source_width || y >= source_height) return {};
        const auto i = static_cast<std::size_t>(y) * source_width + x;
        const int mask = static_cast<std::uint8_t>(source_alpha[i]);
        if (high_precision) {
            auto value = unpack_f16(static_cast<const std::uint64_t*>(source)[i]);
            const float alpha = mask / 255.f;
            return {value.red * alpha, value.green * alpha, value.blue * alpha, value.alpha * alpha};
        }
        const auto value = static_cast<const std::uint32_t*>(source)[i];
        return {static_cast<float>(byte_scale((value >> 16) & 255, mask)), static_cast<float>(byte_scale((value >> 8) & 255, mask)),
            static_cast<float>(byte_scale(value & 255, mask)), static_cast<float>(byte_scale(value >> 24, mask))};
    };
    auto sample = [&](float x, float y) -> PremultipliedColor {
        if (!std::isfinite(x) || !std::isfinite(y) || x < -2 || y < -2 || x > source_width + 2 || y > source_height + 2) return {};
        if (interpolation == 0) return at(static_cast<int>(std::floor(x)), static_cast<int>(std::floor(y)));
        const float px = x - .5f, py = y - .5f;
        const int left = static_cast<int>(std::floor(px)), top = static_cast<int>(std::floor(py));
        const float fx = px - left, fy = py - top;
        const int first = interpolation == 2 ? -1 : 0, count = interpolation == 2 ? 4 : 2;
        PremultipliedColor sum{};
        for (int row = 0; row < count; ++row) for (int col = 0; col < count; ++col) {
            const float weight = interpolation == 2 ? cubic(fx - col + 1) * cubic(fy - row + 1) :
                (col == 0 ? 1-fx : fx) * (row == 0 ? 1-fy : fy);
            if (weight == 0) continue;
            const auto value = at(left + first + col, top + first + row);
            sum.red += value.red * weight; sum.green += value.green * weight; sum.blue += value.blue * weight; sum.alpha += value.alpha * weight;
        }
        return sum;
    };
    for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
        const float gx = tile_left + x + .5f, gy = tile_top + y + .5f;
        auto moved = sample((m[0]*gx + m[1]*gy + m[4]) / (m[6]*gx + m[7]*gy + m[8]) - source_left, (m[2]*gx + m[3]*gy + m[5]) / (m[6]*gx + m[7]*gy + m[8]) - source_top);
        const auto index = static_cast<std::size_t>(y) * stride + x;
        const int retained_mask = 255 - static_cast<std::uint8_t>(destination_alpha[y * width + x]);
        if (high_precision) {
            moved = moved.alpha <= 0 ? PremultipliedColor{} : unpack_f16(pack_f16(moved));
            auto* pixels = static_cast<std::uint64_t*>(destination);
            auto retained = unpack_f16(pixels[index]);
            const float scale = retained_mask / 255.f;
            retained = unpack_f16(pack_f16({retained.red*scale, retained.green*scale, retained.blue*scale, retained.alpha*scale}));
            const float inverse = 1 - moved.alpha;
            pixels[index] = pack_f16({moved.red + retained.red*inverse, moved.green + retained.green*inverse,
                moved.blue + retained.blue*inverse, moved.alpha + retained.alpha*inverse});
        } else {
            const int alpha = std::clamp(static_cast<int>(std::floor(moved.alpha + .5f)), 0, 255);
            auto* pixels = static_cast<std::uint32_t*>(destination);
            const auto original = pixels[index];
            auto channel = [&](float value, int shift) {
                const auto selected = std::clamp(static_cast<int>(std::floor(value + .5f)), 0, alpha);
                const auto retained = byte_scale((original >> shift) & 255, retained_mask);
                return std::min(255u, selected + byte_scale(retained, 255-alpha));
            };
            pixels[index] = (channel(moved.alpha, 24) << 24) | (channel(moved.red, 16) << 16) |
                (channel(moved.green, 8) << 8) | channel(moved.blue, 0);
        }
    }
    env->ReleasePrimitiveArrayCritical(source_alpha_array, source_alpha, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(destination_alpha_array, destination_alpha, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(static_cast<jarray>(source_array), source, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(static_cast<jarray>(destination_array), destination, 0);
    return JNI_TRUE;
}
