#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <limits>
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

float finite_or(float value, float fallback) { return std::isfinite(value) ? value : fallback; }

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

bool compatible_rgba(const LockedBitmap& first, const LockedBitmap& second) {
    return first.pixels != nullptr && second.pixels != nullptr &&
        first.info.width == second.info.width && first.info.height == second.info.height;
}

void clear_bitmap(LockedBitmap& bitmap) {
    for (std::uint32_t y = 0; y < bitmap.info.height; ++y) {
        std::fill_n(bitmap.pixels + static_cast<std::size_t>(y) * bitmap.info.stride,
                    static_cast<std::size_t>(bitmap.info.width) * 4, 0);
    }
}

std::array<int, 4> alpha_bounds(const LockedBitmap& bitmap) {
    int min_x = static_cast<int>(bitmap.info.width);
    int min_y = static_cast<int>(bitmap.info.height);
    int max_x = -1;
    int max_y = -1;
    for (int y = 0; y < static_cast<int>(bitmap.info.height); ++y) {
        const auto* row = bitmap.pixels + static_cast<std::size_t>(y) * bitmap.info.stride;
        for (int x = 0; x < static_cast<int>(bitmap.info.width); ++x) {
            if (row[x * 4 + 3] == 0) continue;
            min_x = std::min(min_x, x);
            min_y = std::min(min_y, y);
            max_x = std::max(max_x, x);
            max_y = std::max(max_y, y);
        }
    }
    return max_x < min_x || max_y < min_y ? std::array<int, 4>{-1, -1, -1, -1} :
        std::array<int, 4>{min_x, min_y, max_x + 1, max_y + 1};
}

jintArray bounds_array(JNIEnv* env, const std::array<int, 4>& bounds) {
    jintArray result = env->NewIntArray(4);
    if (result != nullptr) env->SetIntArrayRegion(result, 0, 4, bounds.data());
    return result;
}

void distance_transform(const std::vector<std::uint8_t>& target, int width, int height,
                        std::vector<int>& distance) {
    const int infinity = width + height + 1;
    const std::size_t count = static_cast<std::size_t>(width) * height;
    distance.resize(count);
    for (std::size_t index = 0; index < count; ++index) distance[index] = target[index] ? 0 : infinity;
    for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
        const int index = y * width + x;
        if (x > 0) distance[index] = std::min(distance[index], distance[index - 1] + 1);
        if (y > 0) distance[index] = std::min(distance[index], distance[index - width] + 1);
    }
    for (int y = height - 1; y >= 0; --y) for (int x = width - 1; x >= 0; --x) {
        const int index = y * width + x;
        if (x + 1 < width) distance[index] = std::min(distance[index], distance[index + 1] + 1);
        if (y + 1 < height) distance[index] = std::min(distance[index], distance[index + width] + 1);
    }
}

bool morph_mask(const std::vector<std::uint8_t>& source, int width, int height, int radius,
                bool dilate, std::vector<std::uint8_t>& output, std::vector<int>& distance) {
    const std::size_t count = static_cast<std::size_t>(width) * height;
    output.assign(count, 0);
    if (radius <= 0) {
        output = source;
        return std::any_of(output.begin(), output.end(), [](std::uint8_t value) { return value != 0; });
    }
    if (dilate) {
        distance_transform(source, width, height, distance);
        for (std::size_t index = 0; index < count; ++index) output[index] = distance[index] <= radius;
    } else {
        std::vector<std::uint8_t> outside(count);
        for (std::size_t index = 0; index < count; ++index) outside[index] = source[index] == 0;
        distance_transform(outside, width, height, distance);
        for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
            const int index = y * width + x;
            const int edge_distance = std::min({x + 1, y + 1, width - x, height - y});
            output[index] = source[index] && distance[index] > radius && edge_distance > radius;
        }
    }
    return std::any_of(output.begin(), output.end(), [](std::uint8_t value) { return value != 0; });
}

void write_white_mask(LockedBitmap& destination, const std::vector<std::uint8_t>& mask) {
    const int width = static_cast<int>(destination.info.width);
    const int height = static_cast<int>(destination.info.height);
    for (int y = 0; y < height; ++y) {
        auto* row = destination.pixels + static_cast<std::size_t>(y) * destination.info.stride;
        for (int x = 0; x < width; ++x) {
            const std::uint8_t value = mask[static_cast<std::size_t>(y) * width + x] ? 255 : 0;
            row[x * 4] = value;
            row[x * 4 + 1] = value;
            row[x * 4 + 2] = value;
            row[x * 4 + 3] = value;
        }
    }
}

std::uint8_t unpremultiplied_channel(const std::uint8_t* pixel, int channel) {
    const int alpha = pixel[3];
    if (alpha == 0) return 0;
    if (alpha == 255) return pixel[channel];
    return static_cast<std::uint8_t>(std::clamp((pixel[channel] * 255 + alpha / 2) / alpha, 0, 255));
}

float dual_density(int mode, float base, float blend) {
    constexpr float epsilon = 1.0f / 65535.0f;
    switch (mode == 0 ? 1 : mode) {
        case 1: return blend + base * (1.0f - blend);
        case 2: return base * blend;
        case 3: return std::min(1.0f, base + blend);
        case 4: return std::max(0.0f, base - blend);
        case 5: return std::min(base, blend);
        case 6: return std::max(base, blend);
        case 7: return 1.0f - (1.0f - base) * (1.0f - blend);
        case 8: return base <= 0.5f ? 2.0f * base * blend :
            1.0f - 2.0f * (1.0f - base) * (1.0f - blend);
        case 9: return blend >= 1.0f - epsilon ? (base <= epsilon ? 0.0f : 1.0f) :
            std::min(1.0f, base / (1.0f - blend));
        case 10: return base >= 1.0f - epsilon ? 1.0f :
            (blend <= epsilon ? 0.0f : 1.0f - std::min(1.0f, (1.0f - base) / blend));
        case 11: return std::max(0.0f, base + blend - 1.0f);
        case 12: return base + blend >= 1.0f ? 1.0f : 0.0f;
        case 13: return base * (1.0f - blend);
        default: return blend + base * (1.0f - blend);
    }
}

float dual_blend_channel(int mode, float base, float blend) {
    if (mode == 0 || mode == 1) return blend;
    return dual_density(mode, base, blend);
}

int floor_mod(int value, int divisor) {
    const int remainder = value % divisor;
    return remainder < 0 ? remainder + divisor : remainder;
}

float effective_texture_luminance(const jint* pixels, int width, int height, int x, int y) {
    const std::uint32_t pixel = static_cast<std::uint32_t>(pixels[floor_mod(y, height) * width + floor_mod(x, width)]);
    const float alpha = ((pixel >> 24) & 0xff) / 255.0f;
    const float luminance = (((pixel >> 16) & 0xff) * 0.299f + ((pixel >> 8) & 0xff) * 0.587f +
                             (pixel & 0xff) * 0.114f) / 255.0f;
    return 1.0f - (1.0f - luminance) * alpha;
}

float lerp_value(float start, float end, float amount) { return start + (end - start) * amount; }

float cubic_value(float a, float b, float c, float d, float t) {
    const float t2 = t * t;
    const float t3 = t2 * t;
    return 0.5f * ((2.0f * b) + (-a + c) * t +
        (2.0f * a - 5.0f * b + 4.0f * c - d) * t2 +
        (-a + 3.0f * b - 3.0f * c + d) * t3);
}

struct TextureSampler {
    const jint* pixels;
    int width;
    int height;
    float x_from_x;
    float x_from_y;
    float x_origin;
    float y_from_x;
    float y_from_y;
    float y_origin;
    int filtering;

    float sample(float x, float y) const {
        const float sample_x = x_from_x * x + x_from_y * y + x_origin;
        const float sample_y = y_from_x * x + y_from_y * y + y_origin;
        const int x0 = static_cast<int>(std::floor(sample_x));
        const int y0 = static_cast<int>(std::floor(sample_y));
        if (filtering <= 0) return effective_texture_luminance(pixels, width, height, x0, y0);
        const float tx = sample_x - std::floor(sample_x);
        const float ty = sample_y - std::floor(sample_y);
        if (filtering == 1) {
            const float top = lerp_value(
                effective_texture_luminance(pixels, width, height, x0, y0),
                effective_texture_luminance(pixels, width, height, x0 + 1, y0), tx);
            const float bottom = lerp_value(
                effective_texture_luminance(pixels, width, height, x0, y0 + 1),
                effective_texture_luminance(pixels, width, height, x0 + 1, y0 + 1), tx);
            return clamp01(lerp_value(top, bottom, ty));
        }
        std::array<float, 4> rows{};
        for (int row = -1; row <= 2; ++row) {
            rows[row + 1] = cubic_value(
                effective_texture_luminance(pixels, width, height, x0 - 1, y0 + row),
                effective_texture_luminance(pixels, width, height, x0, y0 + row),
                effective_texture_luminance(pixels, width, height, x0 + 1, y0 + row),
                effective_texture_luminance(pixels, width, height, x0 + 2, y0 + row), tx);
        }
        return clamp01(cubic_value(rows[0], rows[1], rows[2], rows[3], ty));
    }
};

float texture_mask_value(float luminance, float brightness, float contrast,
                         bool reversed, bool emphasized) {
    const float safe_brightness = std::clamp(finite_or(brightness, 0.0f), -100.0f, 100.0f);
    const float safe_contrast = std::clamp(finite_or(contrast, 0.0f), -100.0f, 100.0f);
    const float brightened = clamp01(luminance + safe_brightness / 100.0f);
    const float contrasted = clamp01((brightened - 0.5f) *
        (1.0f + safe_contrast / 100.0f) + 0.5f);
    const float base = reversed ? contrasted : 1.0f - contrasted;
    return emphasized ? clamp01((base - 0.5f) * 1.5f + 0.5f) : base;
}

float texture_color_dodge(float base, float texture) {
    if (base <= 0.0f) return 0.0f;
    if (texture >= 1.0f) return 1.0f;
    return std::min(1.0f, base / (1.0f - texture));
}

float texture_color_burn(float base, float texture) {
    if (base >= 1.0f) return 1.0f;
    if (texture <= 0.0f) return 0.0f;
    return std::max(0.0f, 1.0f - (1.0f - base) / texture);
}

float texture_density(float base_value, float texture_value, float strength_value,
                      int mode, float texture_edge) {
    const float base = clamp01(base_value);
    const float texture = clamp01(texture_value);
    const float amount = clamp01(strength_value);
    if (amount <= 0.0f || base <= 0.0f) return base;
    float target;
    switch (mode) {
        case 1: {
            if (texture <= 0.5f) target = base - (1.0f - 2.0f * texture) * base * (1.0f - base);
            else {
                const float lifted = base <= 0.25f ? ((16.0f * base - 12.0f) * base + 4.0f) * base : std::sqrt(base);
                target = base + (2.0f * texture - 1.0f) * (lifted - base);
            }
            break;
        }
        case 2: target = base * texture; break;
        case 3: target = std::max(0.0f, base - texture); break;
        case 4: target = std::min(base, texture); break;
        case 5:
        case 9: {
            const float edge_weight = clamp01(4.0f * base * (1.0f - base));
            const float edge = mode == 5 ? clamp01(texture_edge) : texture;
            target = base * (1.0f - edge_weight + edge_weight * edge);
            break;
        }
        case 6: target = base <= 0.5f ? 2.0f * base * texture :
            1.0f - 2.0f * (1.0f - base) * (1.0f - texture); break;
        case 7: target = texture_color_dodge(base, texture); break;
        case 8: {
            const float vivid = texture < 0.5f ? texture_color_burn(base, texture * 2.0f) :
                texture_color_dodge(base, (texture - 0.5f) * 2.0f);
            target = vivid < 0.5f ? 0.0f : 1.0f;
            break;
        }
        default: target = base * texture; break;
    }
    return clamp01(base + (target - base) * amount);
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

extern "C" JNIEXPORT jintArray JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeAlphaBounds(
    JNIEnv* env, jobject, jobject source_bitmap) {
    LockedBitmap source(env, source_bitmap);
    if (source.pixels == nullptr) return bounds_array(env, {-1, -1, -1, -1});
    return bounds_array(env, alpha_bounds(source));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeIsAlphaEmpty(
    JNIEnv* env, jobject, jobject source_bitmap) {
    LockedBitmap source(env, source_bitmap);
    if (source.pixels == nullptr) return JNI_TRUE;
    for (std::uint32_t y = 0; y < source.info.height; ++y) {
        const auto* row = source.pixels + static_cast<std::size_t>(y) * source.info.stride;
        for (std::uint32_t x = 0; x < source.info.width; ++x) if (row[x * 4 + 3] != 0) return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeInvertAlpha(
    JNIEnv* env, jobject, jobject source_bitmap, jobject destination_bitmap) {
    LockedBitmap source(env, source_bitmap);
    LockedBitmap destination(env, destination_bitmap);
    if (!compatible_rgba(source, destination)) return JNI_FALSE;
    bool non_empty = false;
    for (std::uint32_t y = 0; y < source.info.height; ++y) {
        const auto* input = source.pixels + static_cast<std::size_t>(y) * source.info.stride;
        auto* output = destination.pixels + static_cast<std::size_t>(y) * destination.info.stride;
        for (std::uint32_t x = 0; x < source.info.width; ++x) {
            const std::uint8_t alpha = 255 - input[x * 4 + 3];
            non_empty = non_empty || alpha != 0;
            output[x * 4] = alpha;
            output[x * 4 + 1] = alpha;
            output[x * 4 + 2] = alpha;
            output[x * 4 + 3] = alpha;
        }
    }
    return non_empty ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeBoundaryAlpha(
    JNIEnv* env, jobject, jobject source_bitmap, jobject destination_bitmap) {
    LockedBitmap source(env, source_bitmap);
    LockedBitmap destination(env, destination_bitmap);
    if (!compatible_rgba(source, destination)) return JNI_FALSE;
    clear_bitmap(destination);
    const int width = static_cast<int>(source.info.width);
    const int height = static_cast<int>(source.info.height);
    auto selected = [&](int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height &&
            source.pixels[static_cast<std::size_t>(y) * source.info.stride + x * 4 + 3] >= 128;
    };
    bool non_empty = false;
    for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
        const bool inside = selected(x, y);
        if (selected(x - 1, y) == inside && selected(x + 1, y) == inside &&
            selected(x, y - 1) == inside && selected(x, y + 1) == inside) continue;
        for (int dy = -1; dy <= 1; ++dy) for (int dx = -1; dx <= 1; ++dx) {
            const int target_x = x + dx;
            const int target_y = y + dy;
            if (target_x < 0 || target_x >= width || target_y < 0 || target_y >= height) continue;
            auto* pixel = destination.pixels + static_cast<std::size_t>(target_y) * destination.info.stride + target_x * 4;
            std::fill_n(pixel, 4, 255);
            non_empty = true;
        }
    }
    return non_empty ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeMorphAlpha(
    JNIEnv* env, jobject, jobject source_bitmap, jobject destination_bitmap, jint radius_value, jboolean dilate) {
    LockedBitmap source(env, source_bitmap);
    LockedBitmap destination(env, destination_bitmap);
    if (!compatible_rgba(source, destination)) return JNI_FALSE;
    const int width = static_cast<int>(source.info.width);
    const int height = static_cast<int>(source.info.height);
    const std::size_t count = static_cast<std::size_t>(width) * height;
    std::vector<std::uint8_t> input(count);
    for (int y = 0; y < height; ++y) {
        const auto* row = source.pixels + static_cast<std::size_t>(y) * source.info.stride;
        for (int x = 0; x < width; ++x) input[static_cast<std::size_t>(y) * width + x] = row[x * 4 + 3] >= 128;
    }
    std::vector<std::uint8_t> output;
    std::vector<int> distance;
    const bool non_empty = morph_mask(input, width, height, std::max(0, static_cast<int>(radius_value)),
                                      dilate == JNI_TRUE, output, distance);
    write_white_mask(destination, output);
    return non_empty ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeFloodFill(
    JNIEnv* env,
    jobject,
    jobject source_bitmap,
    jobject destination_bitmap,
    jint start_x,
    jint start_y,
    jint tolerance_value,
    jint close_gap_value,
    jint area_scaling_value) {
    LockedBitmap source(env, source_bitmap);
    LockedBitmap destination(env, destination_bitmap);
    const std::array<int, 4> empty_bounds{-1, -1, -1, -1};
    if (!compatible_rgba(source, destination)) return bounds_array(env, empty_bounds);
    const int width = static_cast<int>(source.info.width);
    const int height = static_cast<int>(source.info.height);
    if (start_x < 0 || start_x >= width || start_y < 0 || start_y >= height) return bounds_array(env, empty_bounds);
    const std::size_t count = static_cast<std::size_t>(width) * height;
    const auto* seed = source.pixels + static_cast<std::size_t>(start_y) * source.info.stride + start_x * 4;
    const std::array<int, 4> seed_channels{
        unpremultiplied_channel(seed, 0), unpremultiplied_channel(seed, 1),
        unpremultiplied_channel(seed, 2), seed[3],
    };
    const int tolerance = std::clamp(static_cast<int>(tolerance_value), 0, 255);
    thread_local std::vector<std::uint8_t> fillable;
    thread_local std::vector<std::uint8_t> covered;
    thread_local std::vector<std::uint8_t> scratch_mask;
    thread_local std::vector<int> distance;
    thread_local std::vector<int> stack;
    fillable.assign(count, 0);
    for (int y = 0; y < height; ++y) {
        const auto* row = source.pixels + static_cast<std::size_t>(y) * source.info.stride;
        for (int x = 0; x < width; ++x) {
            const auto* pixel = row + x * 4;
            const bool both_opaque = pixel[3] == 255 && seed_channels[3] == 255;
            const int red = both_opaque ? pixel[0] : unpremultiplied_channel(pixel, 0);
            const int green = both_opaque ? pixel[1] : unpremultiplied_channel(pixel, 1);
            const int blue = both_opaque ? pixel[2] : unpremultiplied_channel(pixel, 2);
            fillable[static_cast<std::size_t>(y) * width + x] =
                std::abs(red - seed_channels[0]) <= tolerance &&
                std::abs(green - seed_channels[1]) <= tolerance &&
                std::abs(blue - seed_channels[2]) <= tolerance &&
                std::abs(static_cast<int>(pixel[3]) - seed_channels[3]) <= tolerance;
        }
    }
    const int close_gap = std::clamp(static_cast<int>(close_gap_value), 0, 64);
    if (close_gap > 0) {
        scratch_mask.resize(count);
        for (std::size_t index = 0; index < count; ++index) scratch_mask[index] = fillable[index] == 0;
        distance_transform(scratch_mask, width, height, distance);
        for (std::size_t index = 0; index < count; ++index) fillable[index] = distance[index] > close_gap;
    }

    covered.assign(count, 0);
    stack.clear();
    if (stack.capacity() < std::min<std::size_t>(count, 4096)) stack.reserve(std::min<std::size_t>(count, 4096));
    auto push = [&](int x, int y) {
        const int index = y * width + x;
        if (covered[index] || fillable[index] != 1) return;
        stack.push_back(index);
        fillable[index] = 2;
    };
    push(start_x, start_y);
    while (!stack.empty()) {
        const int index = stack.back();
        stack.pop_back();
        if (covered[index]) continue;
        const int y = index / width;
        int x = index % width;
        while (x >= 0 && !covered[y * width + x] && fillable[y * width + x] != 0) --x;
        ++x;
        bool span_up = false;
        bool span_down = false;
        while (x < width && !covered[y * width + x] && fillable[y * width + x] != 0) {
            covered[y * width + x] = 1;
            if (y > 0) {
                const bool candidate = !covered[(y - 1) * width + x] && fillable[(y - 1) * width + x] == 1;
                if (!span_up && candidate) { push(x, y - 1); span_up = true; }
                else if (span_up && !candidate) span_up = false;
            }
            if (y + 1 < height) {
                const bool candidate = !covered[(y + 1) * width + x] && fillable[(y + 1) * width + x] == 1;
                if (!span_down && candidate) { push(x, y + 1); span_down = true; }
                else if (span_down && !candidate) span_down = false;
            }
            ++x;
        }
    }
    const int scaling = std::clamp(static_cast<int>(area_scaling_value), -64, 64);
    if (scaling != 0) {
        morph_mask(covered, width, height, std::abs(scaling), scaling > 0, scratch_mask, distance);
        covered.swap(scratch_mask);
    }
    int min_x = width;
    int min_y = height;
    int max_x = -1;
    int max_y = -1;
    for (int y = 0; y < height; ++y) {
        auto* row = destination.pixels + static_cast<std::size_t>(y) * destination.info.stride;
        for (int x = 0; x < width; ++x) {
            const std::uint8_t value = covered[static_cast<std::size_t>(y) * width + x] ? 255 : 0;
            row[x * 4] = value;
            row[x * 4 + 1] = value;
            row[x * 4 + 2] = value;
            row[x * 4 + 3] = value;
            if (value == 0) continue;
            min_x = std::min(min_x, x);
            min_y = std::min(min_y, y);
            max_x = std::max(max_x, x);
            max_y = std::max(max_y, y);
        }
    }
    return bounds_array(env, max_x < min_x ? empty_bounds : std::array<int, 4>{min_x, min_y, max_x + 1, max_y + 1});
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeCompositeDualBrush(
    JNIEnv* env,
    jobject,
    jobject primary_bitmap,
    jobject secondary_bitmap,
    jobject output_bitmap,
    jint left_value,
    jint top_value,
    jint right_value,
    jint bottom_value,
    jint mode_code,
    jboolean apply_rgb) {
    LockedBitmap primary(env, primary_bitmap);
    LockedBitmap secondary(env, secondary_bitmap);
    LockedBitmap output(env, output_bitmap);
    if (!compatible_rgba(primary, secondary) || !compatible_rgba(primary, output)) return JNI_FALSE;
    const int width = static_cast<int>(primary.info.width);
    const int height = static_cast<int>(primary.info.height);
    const int left = std::clamp(static_cast<int>(left_value), 0, width);
    const int top = std::clamp(static_cast<int>(top_value), 0, height);
    const int right = std::clamp(static_cast<int>(right_value), 0, width);
    const int bottom = std::clamp(static_cast<int>(bottom_value), 0, height);
    if (right <= left || bottom <= top) return JNI_FALSE;
    constexpr float epsilon = 1.0f / 65535.0f;
    for (int y = top; y < bottom; ++y) {
        const auto* primary_row = primary.pixels + static_cast<std::size_t>(y) * primary.info.stride;
        const auto* secondary_row = secondary.pixels + static_cast<std::size_t>(y) * secondary.info.stride;
        auto* output_row = output.pixels + static_cast<std::size_t>(y) * output.info.stride;
        for (int x = left; x < right; ++x) {
            const auto* base_pixel = primary_row + x * 4;
            const auto* source_pixel = secondary_row + x * 4;
            auto* target_pixel = output_row + x * 4;
            const float base_alpha = base_pixel[3] / 255.0f;
            const float source_alpha = source_pixel[3] / 255.0f;
            std::array<std::uint8_t, 4> composed{};
            if (apply_rgb == JNI_FALSE) {
                const float output_alpha = clamp01(dual_density(mode_code, base_alpha, source_alpha));
                const auto* color_pixel = base_alpha > epsilon ? base_pixel : source_pixel;
                for (int channel = 0; channel < 3; ++channel) {
                    const float color = unpremultiplied_channel(color_pixel, channel) / 255.0f;
                    composed[channel] = byte_from_unit(color * output_alpha);
                }
                composed[3] = byte_from_unit(output_alpha);
            } else {
                const float output_alpha = source_alpha + base_alpha * (1.0f - source_alpha);
                for (int channel = 0; channel < 3; ++channel) {
                    const float base = unpremultiplied_channel(base_pixel, channel) / 255.0f;
                    const float source = unpremultiplied_channel(source_pixel, channel) / 255.0f;
                    const float blended = clamp01(dual_blend_channel(mode_code, base, source));
                    const float premultiplied =
                        (1.0f - source_alpha) * base * base_alpha +
                        (1.0f - base_alpha) * source * source_alpha +
                        base_alpha * source_alpha * blended;
                    composed[channel] = byte_from_unit(premultiplied);
                }
                composed[3] = byte_from_unit(output_alpha);
            }
            for (int channel = 0; channel < 4; ++channel) {
                target_pixel[channel] = composed[channel];
            }
        }
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v3_tools_NativeRasterOps_nativeApplyCspStrokeTexture(
    JNIEnv* env,
    jobject,
    jobject stroke_bitmap,
    jint content_width_value,
    jint content_height_value,
    jint origin_x,
    jint origin_y,
    jint page_width_value,
    jint page_height_value,
    jintArray texture_pixels_array,
    jint texture_width_value,
    jint texture_height_value,
    jfloat density_value,
    jfloat scale_percent_value,
    jfloat rotation_degrees_value,
    jfloat offset_x_value,
    jfloat offset_y_value,
    jint filtering_mode,
    jfloat brightness,
    jfloat contrast,
    jboolean reversed,
    jboolean emphasized,
    jint composite_mode,
    jboolean alpha_threshold_enabled,
    jfloat alpha_threshold_value) {
    LockedBitmap stroke(env, stroke_bitmap);
    if (stroke.pixels == nullptr || texture_pixels_array == nullptr) return JNI_FALSE;
    const int content_width = std::min(static_cast<int>(content_width_value), static_cast<int>(stroke.info.width));
    const int content_height = std::min(static_cast<int>(content_height_value), static_cast<int>(stroke.info.height));
    const int texture_width = static_cast<int>(texture_width_value);
    const int texture_height = static_cast<int>(texture_height_value);
    const int page_width = std::max(1, static_cast<int>(page_width_value));
    const int page_height = std::max(1, static_cast<int>(page_height_value));
    const std::int64_t required_texture_pixels = static_cast<std::int64_t>(texture_width) * texture_height;
    if (content_width <= 0 || content_height <= 0 || texture_width <= 0 || texture_height <= 0 ||
        required_texture_pixels > std::numeric_limits<jsize>::max() ||
        static_cast<std::int64_t>(env->GetArrayLength(texture_pixels_array)) < required_texture_pixels) {
        return JNI_FALSE;
    }
    auto* texture_pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(texture_pixels_array, nullptr));
    if (texture_pixels == nullptr) return JNI_FALSE;

    const float scale = std::clamp(finite_or(scale_percent_value / 100.0f, 1.0f), 0.01f, 40.0f);
    const float rotation = finite_or(rotation_degrees_value, 0.0f);
    const float offset_x = std::clamp(finite_or(offset_x_value, 0.0f), 0.0f, 1.0f);
    const float offset_y = std::clamp(finite_or(offset_y_value, 0.0f), 0.0f, 1.0f);
    const float radians = -rotation * static_cast<float>(3.14159265358979323846 / 180.0);
    const float cosine = std::cos(radians);
    const float sine = std::sin(radians);
    const float inverse_scale = 1.0f / scale;
    const float texture_scale_x = texture_width / static_cast<float>(page_width);
    const float texture_scale_y = texture_height / static_cast<float>(page_height);
    const float translated_x = 0.5f - page_width / 2.0f;
    const float translated_y = 0.5f - page_height / 2.0f;
    TextureSampler sampler{
        texture_pixels,
        texture_width,
        texture_height,
        cosine * inverse_scale * texture_scale_x,
        -sine * inverse_scale * texture_scale_x,
        (cosine * translated_x - sine * translated_y) * inverse_scale * texture_scale_x +
            texture_width / 2.0f + offset_x * texture_width,
        sine * inverse_scale * texture_scale_y,
        cosine * inverse_scale * texture_scale_y,
        (sine * translated_x + cosine * translated_y) * inverse_scale * texture_scale_y +
            texture_height / 2.0f + offset_y * texture_height,
        std::clamp(static_cast<int>(filtering_mode), 0, 2),
    };
    const bool outline = composite_mode == 5;
    const std::size_t count = static_cast<std::size_t>(content_width) * content_height;
    thread_local std::vector<float> texture_masks;
    if (outline) {
        texture_masks.resize(count);
        for (int local_y = 0; local_y < content_height; ++local_y) for (int local_x = 0; local_x < content_width; ++local_x) {
            texture_masks[static_cast<std::size_t>(local_y) * content_width + local_x] = texture_mask_value(
                sampler.sample(origin_x + local_x, origin_y + local_y), brightness, contrast,
                reversed == JNI_TRUE, emphasized == JNI_TRUE);
        }
    }
    const float cutoff = clamp01(finite_or(alpha_threshold_value, 0.0f)) * 255.0f;
    const float density = clamp01(finite_or(density_value, 0.0f));
    for (int local_y = 0; local_y < content_height; ++local_y) {
        auto* row = stroke.pixels + static_cast<std::size_t>(local_y) * stroke.info.stride;
        for (int local_x = 0; local_x < content_width; ++local_x) {
            auto* pixel = row + local_x * 4;
            const int source_alpha_byte = pixel[3];
            if (source_alpha_byte == 0) continue;
            const int page_x = origin_x + local_x;
            const int page_y = origin_y + local_y;
            const std::size_t index = static_cast<std::size_t>(local_y) * content_width + local_x;
            const float texture = outline ? texture_masks[index] : texture_mask_value(
                sampler.sample(page_x, page_y), brightness, contrast,
                reversed == JNI_TRUE, emphasized == JNI_TRUE);
            float texture_edge = 0.0f;
            if (outline) {
                const float left = local_x > 0 ? texture_masks[index - 1] : texture_mask_value(
                    sampler.sample(page_x - 1, page_y), brightness, contrast, reversed == JNI_TRUE, emphasized == JNI_TRUE);
                const float right = local_x + 1 < content_width ? texture_masks[index + 1] : texture_mask_value(
                    sampler.sample(page_x + 1, page_y), brightness, contrast, reversed == JNI_TRUE, emphasized == JNI_TRUE);
                const float top = local_y > 0 ? texture_masks[index - content_width] : texture_mask_value(
                    sampler.sample(page_x, page_y - 1), brightness, contrast, reversed == JNI_TRUE, emphasized == JNI_TRUE);
                const float bottom = local_y + 1 < content_height ? texture_masks[index + content_width] : texture_mask_value(
                    sampler.sample(page_x, page_y + 1), brightness, contrast, reversed == JNI_TRUE, emphasized == JNI_TRUE);
                texture_edge = clamp01(std::abs(right - left) + std::abs(bottom - top));
            }
            const float composed = texture_density(source_alpha_byte / 255.0f, texture, density, composite_mode, texture_edge);
            const int raw_alpha = std::clamp(static_cast<int>(composed * 255.0f), 0, 255);
            const int output_alpha = alpha_threshold_enabled == JNI_TRUE ? (raw_alpha > cutoff ? 255 : 0) : raw_alpha;
            for (int channel = 0; channel < 3; ++channel) {
                const float color = unpremultiplied_channel(pixel, channel) / 255.0f;
                pixel[channel] = byte_from_unit(color * (output_alpha / 255.0f));
            }
            pixel[3] = static_cast<std::uint8_t>(output_alpha);
        }
    }
    env->ReleasePrimitiveArrayCritical(texture_pixels_array, texture_pixels, JNI_ABORT);
    return JNI_TRUE;
}
