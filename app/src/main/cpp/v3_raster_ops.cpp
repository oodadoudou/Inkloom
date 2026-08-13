#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <vector>

namespace {

struct LockedBitmap {
    JNIEnv* env;
    jobject bitmap;
    AndroidBitmapInfo info{};
    std::uint8_t* pixels = nullptr;

    LockedBitmap(JNIEnv* env_value, jobject bitmap_value) : env(env_value), bitmap(bitmap_value) {
        if (bitmap == nullptr || AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
            info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
            AndroidBitmap_lockPixels(env, bitmap, reinterpret_cast<void**>(&pixels)) != ANDROID_BITMAP_RESULT_SUCCESS) {
            pixels = nullptr;
        }
    }

    ~LockedBitmap() {
        if (pixels != nullptr) AndroidBitmap_unlockPixels(env, bitmap);
    }

    LockedBitmap(const LockedBitmap&) = delete;
    LockedBitmap& operator=(const LockedBitmap&) = delete;
};

float clamp01(float value) { return std::clamp(value, 0.0f, 1.0f); }

std::array<float, 3> srgb_to_linear(const std::array<float, 3>& value) {
    std::array<float, 3> output{};
    for (int index = 0; index < 3; ++index) {
        const float channel = clamp01(value[index]);
        output[index] = channel <= 0.04045f ? channel / 12.92f :
            std::pow((channel + 0.055f) / 1.055f, 2.4f);
    }
    return output;
}

std::array<float, 3> linear_to_srgb(const std::array<float, 3>& value) {
    std::array<float, 3> output{};
    for (int index = 0; index < 3; ++index) {
        const float channel = clamp01(value[index]);
        output[index] = channel <= 0.0031308f ? channel * 12.92f :
            1.055f * std::pow(channel, 1.0f / 2.4f) - 0.055f;
    }
    return output;
}

std::array<float, 3> to_oklab(const std::array<float, 3>& encoded) {
    const auto rgb = srgb_to_linear(encoded);
    float l = 0.4122214708f * rgb[0] + 0.5363325363f * rgb[1] + 0.0514459929f * rgb[2];
    float m = 0.2119034982f * rgb[0] + 0.6806995451f * rgb[1] + 0.1073969566f * rgb[2];
    float s = 0.0883024619f * rgb[0] + 0.2817188376f * rgb[1] + 0.6299787005f * rgb[2];
    l = std::cbrt(std::max(l, 0.0f));
    m = std::cbrt(std::max(m, 0.0f));
    s = std::cbrt(std::max(s, 0.0f));
    return {
        0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
    };
}

std::array<float, 3> from_oklab(const std::array<float, 3>& lab) {
    float l = lab[0] + 0.3963377774f * lab[1] + 0.2158037573f * lab[2];
    float m = lab[0] - 0.1055613458f * lab[1] - 0.0638541728f * lab[2];
    float s = lab[0] - 0.0894841775f * lab[1] - 1.2914855480f * lab[2];
    l = l * l * l;
    m = m * m * m;
    s = s * s * s;
    return linear_to_srgb({
        4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
    });
}

std::uint8_t byte_from_unit(float value) {
    return static_cast<std::uint8_t>(std::lround(clamp01(value) * 255.0f));
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeCompositePigment(
    JNIEnv* env,
    jobject,
    jobject base_bitmap,
    jobject fresh_bitmap,
    jint left,
    jint top,
    jint requested_width,
    jint requested_height,
    jfloat fresh_ratio_value,
    jfloat source_strength_value,
    jboolean local_pigment,
    jboolean perceptual,
    jfloat brightness_correction) {
    LockedBitmap base(env, base_bitmap);
    LockedBitmap fresh(env, fresh_bitmap);
    if (base.pixels == nullptr || fresh.pixels == nullptr || left < 0 || top < 0) return JNI_FALSE;
    const int width = std::min({requested_width, static_cast<int>(fresh.info.width),
                                static_cast<int>(base.info.width) - left});
    const int height = std::min({requested_height, static_cast<int>(fresh.info.height),
                                 static_cast<int>(base.info.height) - top});
    if (width <= 0 || height <= 0) return JNI_FALSE;
    const float fresh_ratio = clamp01(fresh_ratio_value);
    const float source_strength = std::max(source_strength_value, 0.0f);
    bool changed = false;

    for (int y = 0; y < height; ++y) {
        auto* under_row = base.pixels + static_cast<std::size_t>(top + y) * base.info.stride + left * 4;
        const auto* fresh_row = fresh.pixels + static_cast<std::size_t>(y) * fresh.info.stride;
        for (int x = 0; x < width; ++x) {
            auto* under = under_row + x * 4;
            const auto* incoming = fresh_row + x * 4;
            const float under_alpha = under[3] / 255.0f;
            const float original_fresh_alpha = incoming[3] / 255.0f;
            if (original_fresh_alpha <= 0.0001f) continue;
            const float fresh_alpha = clamp01(original_fresh_alpha * source_strength);
            std::array<float, 3> under_rgb{};
            std::array<float, 3> fresh_rgb{};
            for (int channel = 0; channel < 3; ++channel) {
                under_rgb[channel] = under_alpha <= 0.0001f ? 0.0f :
                    clamp01((under[channel] / 255.0f) / under_alpha);
                fresh_rgb[channel] = clamp01((incoming[channel] / 255.0f) / original_fresh_alpha);
            }
            auto pigment = fresh_rgb;
            if (local_pigment == JNI_FALSE && under_alpha > 0.0001f) {
                if (perceptual == JNI_TRUE) {
                    const auto first = to_oklab(under_rgb);
                    const auto second = to_oklab(fresh_rgb);
                    std::array<float, 3> mixed{};
                    for (int channel = 0; channel < 3; ++channel) {
                        mixed[channel] = first[channel] + (second[channel] - first[channel]) * fresh_ratio;
                    }
                    const float balance = 4.0f * fresh_ratio * (1.0f - fresh_ratio);
                    mixed[0] = clamp01(mixed[0] + brightness_correction * 0.028f * balance);
                    pigment = from_oklab(mixed);
                } else {
                    for (int channel = 0; channel < 3; ++channel) {
                        pigment[channel] = under_rgb[channel] +
                            (fresh_rgb[channel] - under_rgb[channel]) * fresh_ratio;
                    }
                }
            }
            const float output_alpha = fresh_alpha + under_alpha * (1.0f - fresh_alpha);
            std::array<std::uint8_t, 4> output{};
            for (int channel = 0; channel < 3; ++channel) {
                const float premultiplied = pigment[channel] * fresh_alpha +
                    under_rgb[channel] * under_alpha * (1.0f - fresh_alpha);
                output[channel] = byte_from_unit(premultiplied);
            }
            output[3] = byte_from_unit(output_alpha);
            for (int channel = 0; channel < 4; ++channel) {
                changed = changed || under[channel] != output[channel];
                under[channel] = output[channel];
            }
        }
    }
    return changed ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeBlur(
    JNIEnv* env,
    jobject,
    jobject source_bitmap,
    jobject destination_bitmap,
    jint requested_width,
    jint requested_height,
    jfloat radius_value) {
    LockedBitmap source(env, source_bitmap);
    LockedBitmap destination(env, destination_bitmap);
    if (source.pixels == nullptr || destination.pixels == nullptr) return JNI_FALSE;
    const int width = std::min({requested_width, static_cast<int>(source.info.width),
                                static_cast<int>(destination.info.width)});
    const int height = std::min({requested_height, static_cast<int>(source.info.height),
                                 static_cast<int>(destination.info.height)});
    const int radius = std::max(0, static_cast<int>(std::ceil(radius_value)));
    if (width <= 0 || height <= 0) return JNI_FALSE;
    if (radius == 0) {
        for (int y = 0; y < height; ++y) {
            std::copy_n(source.pixels + static_cast<std::size_t>(y) * source.info.stride,
                        static_cast<std::size_t>(width) * 4,
                        destination.pixels + static_cast<std::size_t>(y) * destination.info.stride);
        }
        return JNI_TRUE;
    }

    const int diameter = radius * 2 + 1;
    const std::size_t required_bytes = static_cast<std::size_t>(width) * height * 4;
    thread_local std::vector<std::uint8_t> horizontal;
    if (horizontal.size() < required_bytes) horizontal.resize(required_bytes);
    for (int y = 0; y < height; ++y) {
        const auto* row = source.pixels + static_cast<std::size_t>(y) * source.info.stride;
        std::array<std::int64_t, 4> sum{};
        for (int sample_x = 0; sample_x <= radius && sample_x < width; ++sample_x) {
            for (int channel = 0; channel < 4; ++channel) sum[channel] += row[sample_x * 4 + channel];
        }
        for (int x = 0; x < width; ++x) {
            auto* output = horizontal.data() + (static_cast<std::size_t>(y) * width + x) * 4;
            for (int channel = 0; channel < 4; ++channel) output[channel] =
                static_cast<std::uint8_t>(sum[channel] / diameter);
            const int leaving = x - radius;
            const int entering = x + radius + 1;
            if (leaving >= 0) for (int channel = 0; channel < 4; ++channel) sum[channel] -= row[leaving * 4 + channel];
            if (entering < width) for (int channel = 0; channel < 4; ++channel) sum[channel] += row[entering * 4 + channel];
        }
    }
    for (int x = 0; x < width; ++x) {
        std::array<std::int64_t, 4> sum{};
        for (int sample_y = 0; sample_y <= radius && sample_y < height; ++sample_y) {
            const auto* sample = horizontal.data() + (static_cast<std::size_t>(sample_y) * width + x) * 4;
            for (int channel = 0; channel < 4; ++channel) sum[channel] += sample[channel];
        }
        for (int y = 0; y < height; ++y) {
            auto* output = destination.pixels + static_cast<std::size_t>(y) * destination.info.stride + x * 4;
            for (int channel = 0; channel < 4; ++channel) output[channel] =
                static_cast<std::uint8_t>(sum[channel] / diameter);
            const int leaving = y - radius;
            const int entering = y + radius + 1;
            if (leaving >= 0) {
                const auto* sample = horizontal.data() + (static_cast<std::size_t>(leaving) * width + x) * 4;
                for (int channel = 0; channel < 4; ++channel) sum[channel] -= sample[channel];
            }
            if (entering < height) {
                const auto* sample = horizontal.data() + (static_cast<std::size_t>(entering) * width + x) * 4;
                for (int channel = 0; channel < 4; ++channel) sum[channel] += sample[channel];
            }
        }
    }
    return JNI_TRUE;
}
