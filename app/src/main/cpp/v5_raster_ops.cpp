#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
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

float sample_alpha_mask(const jbyte* alpha, int width, int height, float u, float v) {
    if (alpha == nullptr || width <= 0 || height <= 0 || !std::isfinite(u) || !std::isfinite(v) ||
        u < 0.0f || u > 1.0f || v < 0.0f || v > 1.0f) return 0.0f;
    const float px = u * (width - 1);
    const float py = v * (height - 1);
    const int x0 = static_cast<int>(px);
    const int y0 = static_cast<int>(py);
    const int x1 = std::min(x0 + 1, width - 1);
    const int y1 = std::min(y0 + 1, height - 1);
    const float tx = px - x0;
    const float ty = py - y0;
    const float top_left = static_cast<std::uint8_t>(alpha[y0 * width + x0]);
    const float top_right = static_cast<std::uint8_t>(alpha[y0 * width + x1]);
    const float bottom_left = static_cast<std::uint8_t>(alpha[y1 * width + x0]);
    const float bottom_right = static_cast<std::uint8_t>(alpha[y1 * width + x1]);
    const float top_value = top_left + (top_right - top_left) * tx;
    const float bottom_value = bottom_left + (bottom_right - bottom_left) * tx;
    return (top_value + (bottom_value - top_value) * ty) / 255.0f;
}

std::uint32_t composite_premultiplied_pixel(
    std::uint32_t destination_pixel,
    std::uint32_t source_pixel,
    int blend_mode) {
    const int source_alpha = static_cast<int>(source_pixel >> 24);
    if (blend_mode == 0) {
        if (source_alpha == 255) return source_pixel;
        if (source_alpha == 0) return destination_pixel;
        const int inverse = 255 - source_alpha;
        const int destination_alpha = static_cast<int>(destination_pixel >> 24);
        const int alpha = std::min(255, source_alpha + destination_alpha * inverse / 255);
        const int red = std::min(255, static_cast<int>((source_pixel >> 16) & 0xff) +
            static_cast<int>((destination_pixel >> 16) & 0xff) * inverse / 255);
        const int green = std::min(255, static_cast<int>((source_pixel >> 8) & 0xff) +
            static_cast<int>((destination_pixel >> 8) & 0xff) * inverse / 255);
        const int blue = std::min(255, static_cast<int>(source_pixel & 0xff) +
            static_cast<int>(destination_pixel & 0xff) * inverse / 255);
        return static_cast<std::uint32_t>((alpha << 24) | (red << 16) | (green << 8) | blue);
    }
    if (blend_mode == 1) {
        const int keep = 255 - source_alpha;
        return static_cast<std::uint32_t>(
            ((((destination_pixel >> 24) & 0xff) * keep / 255) << 24) |
            ((((destination_pixel >> 16) & 0xff) * keep / 255) << 16) |
            ((((destination_pixel >> 8) & 0xff) * keep / 255) << 8) |
            ((destination_pixel & 0xff) * keep / 255));
    }
    const int destination_alpha = static_cast<int>(destination_pixel >> 24);
    if (destination_alpha == 0 || source_alpha == 0) return destination_pixel;
    const int inverse = 255 - source_alpha;
    const int red = std::min(destination_alpha,
        (static_cast<int>((source_pixel >> 16) & 0xff) * destination_alpha +
         static_cast<int>((destination_pixel >> 16) & 0xff) * inverse) / 255);
    const int green = std::min(destination_alpha,
        (static_cast<int>((source_pixel >> 8) & 0xff) * destination_alpha +
         static_cast<int>((destination_pixel >> 8) & 0xff) * inverse) / 255);
    const int blue = std::min(destination_alpha,
        (static_cast<int>(source_pixel & 0xff) * destination_alpha +
         static_cast<int>(destination_pixel & 0xff) * inverse) / 255);
    return static_cast<std::uint32_t>((destination_alpha << 24) | (red << 16) | (green << 8) | blue);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeCompositePremultipliedTile(
    JNIEnv* env,
    jobject,
    jintArray destination_array,
    jintArray source_array,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jint blend_mode) {
    if (destination_array == nullptr || source_array == nullptr || stride <= 0 ||
        left < 0 || top < 0 || right <= left || bottom <= top || right > stride ||
        blend_mode < 0 || blend_mode > 2) return JNI_FALSE;
    const jsize destination_size = env->GetArrayLength(destination_array);
    const jsize source_size = env->GetArrayLength(source_array);
    const std::int64_t required = static_cast<std::int64_t>(bottom - 1) * stride + right;
    if (required > destination_size || required > source_size) return JNI_FALSE;

    auto* destination = static_cast<jint*>(env->GetPrimitiveArrayCritical(destination_array, nullptr));
    if (destination == nullptr) return JNI_FALSE;
    auto* source = static_cast<jint*>(env->GetPrimitiveArrayCritical(source_array, nullptr));
    if (source == nullptr) {
        env->ReleasePrimitiveArrayCritical(destination_array, destination, JNI_ABORT);
        return JNI_FALSE;
    }

    for (int y = top; y < bottom; ++y) {
        const int row = y * stride;
        for (int x = left; x < right; ++x) {
            const int index = row + x;
            const std::uint32_t destination_pixel = static_cast<std::uint32_t>(destination[index]);
            const std::uint32_t source_pixel = static_cast<std::uint32_t>(source[index]);
            const int source_alpha = static_cast<int>(source_pixel >> 24);
            std::uint32_t output = destination_pixel;
            if (blend_mode == 0) {
                if (source_alpha == 255) {
                    output = source_pixel;
                } else if (source_alpha != 0) {
                    const int inverse = 255 - source_alpha;
                    const int destination_alpha = static_cast<int>(destination_pixel >> 24);
                    const int alpha = std::min(255, source_alpha + destination_alpha * inverse / 255);
                    const int red = std::min(255, static_cast<int>((source_pixel >> 16) & 0xff) +
                        static_cast<int>((destination_pixel >> 16) & 0xff) * inverse / 255);
                    const int green = std::min(255, static_cast<int>((source_pixel >> 8) & 0xff) +
                        static_cast<int>((destination_pixel >> 8) & 0xff) * inverse / 255);
                    const int blue = std::min(255, static_cast<int>(source_pixel & 0xff) +
                        static_cast<int>(destination_pixel & 0xff) * inverse / 255);
                    output = static_cast<std::uint32_t>((alpha << 24) | (red << 16) | (green << 8) | blue);
                }
            } else if (blend_mode == 1) {
                const int keep = 255 - source_alpha;
                output = static_cast<std::uint32_t>(
                    ((((destination_pixel >> 24) & 0xff) * keep / 255) << 24) |
                    ((((destination_pixel >> 16) & 0xff) * keep / 255) << 16) |
                    ((((destination_pixel >> 8) & 0xff) * keep / 255) << 8) |
                    ((destination_pixel & 0xff) * keep / 255));
            } else {
                const int destination_alpha = static_cast<int>(destination_pixel >> 24);
                if (destination_alpha != 0 && source_alpha != 0) {
                    const int inverse = 255 - source_alpha;
                    const int red = std::min(destination_alpha,
                        (static_cast<int>((source_pixel >> 16) & 0xff) * destination_alpha +
                         static_cast<int>((destination_pixel >> 16) & 0xff) * inverse) / 255);
                    const int green = std::min(destination_alpha,
                        (static_cast<int>((source_pixel >> 8) & 0xff) * destination_alpha +
                         static_cast<int>((destination_pixel >> 8) & 0xff) * inverse) / 255);
                    const int blue = std::min(destination_alpha,
                        (static_cast<int>(source_pixel & 0xff) * destination_alpha +
                         static_cast<int>(destination_pixel & 0xff) * inverse) / 255);
                    output = static_cast<std::uint32_t>((destination_alpha << 24) |
                                                        (red << 16) | (green << 8) | blue);
                }
            }
            destination[index] = static_cast<jint>(output);
        }
    }

    env->ReleasePrimitiveArrayCritical(source_array, source, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(destination_array, destination, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeMaterializePremultipliedTile(
    JNIEnv* env,
    jobject,
    jintArray contribution_array,
    jintArray backdrop_array,
    jint stride,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jint blend_mode) {
    if (contribution_array == nullptr || backdrop_array == nullptr || stride <= 0 ||
        left < 0 || top < 0 || right <= left || bottom <= top || right > stride ||
        blend_mode < 0 || blend_mode > 2) return -1;
    const jsize contribution_size = env->GetArrayLength(contribution_array);
    if (contribution_size != env->GetArrayLength(backdrop_array) ||
        contribution_size < stride * stride || bottom > stride) return -1;

    auto* contribution = static_cast<jint*>(env->GetPrimitiveArrayCritical(contribution_array, nullptr));
    if (contribution == nullptr) return -1;
    auto* backdrop = static_cast<jint*>(env->GetPrimitiveArrayCritical(backdrop_array, nullptr));
    if (backdrop == nullptr) {
        env->ReleasePrimitiveArrayCritical(contribution_array, contribution, JNI_ABORT);
        return -1;
    }

    bool changed = false;
    int non_transparent = 0;
    for (int y = 0; y < stride; ++y) {
        const int row = y * stride;
        for (int x = 0; x < stride; ++x) {
            const int index = row + x;
            const std::uint32_t before = static_cast<std::uint32_t>(backdrop[index]);
            const std::uint32_t after = x >= left && x < right && y >= top && y < bottom
                ? composite_premultiplied_pixel(
                    before, static_cast<std::uint32_t>(contribution[index]), blend_mode)
                : before;
            contribution[index] = static_cast<jint>(after);
            changed = changed || after != before;
            non_transparent += (after >> 24) != 0;
        }
    }
    env->ReleasePrimitiveArrayCritical(backdrop_array, backdrop, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(contribution_array, contribution, 0);
    return (changed ? (1 << 30) : 0) | non_transparent;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeDepositRoundWet(
    JNIEnv* env,
    jobject,
    jintArray destination_array,
    jint stride,
    jint tile_left,
    jint tile_top,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jfloat center_x,
    jfloat center_y,
    jfloat outer_radius,
    jfloat inner_radius,
    jfloat inverse_edge,
    jfloat base_deposition,
    jint transfer_mode,
    jfloat reservoir_alpha,
    jfloat reservoir_red,
    jfloat reservoir_green,
    jfloat reservoir_blue,
    jfloat opacity_limit) {
    if (!std::isfinite(opacity_limit) || opacity_limit < 0.f || opacity_limit > 1.f) return -1;
    if (destination_array == nullptr || stride <= 0 || left < tile_left || top < tile_top ||
        right <= left || bottom <= top || right - tile_left > stride || bottom - tile_top > stride ||
        !std::isfinite(center_x) || !std::isfinite(center_y) || outer_radius <= 0.0f ||
        inner_radius < 0.0f || inverse_edge <= 0.0f || transfer_mode < 0 || transfer_mode > 5) return -1;
    const jsize destination_size = env->GetArrayLength(destination_array);
    if (destination_size < stride * stride) return -1;
    auto* destination = static_cast<jint*>(env->GetPrimitiveArrayCritical(destination_array, nullptr));
    if (destination == nullptr) return -1;

    static constexpr float exponents[] = {0.65f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    const float exponent = exponents[transfer_mode];
    const float outer_squared = outer_radius * outer_radius;
    const float inner_squared = inner_radius * inner_radius;
    bool changed = false;
    for (int y = top; y < bottom; ++y) {
        const int local_y = y - tile_top;
        for (int x = left; x < right; ++x) {
            const float dx = x + 0.5f - center_x;
            const float dy = y + 0.5f - center_y;
            const float distance_squared = dx * dx + dy * dy;
            if (distance_squared >= outer_squared) continue;
            const float coverage = distance_squared <= inner_squared ? 1.0f :
                (outer_radius - std::sqrt(distance_squared)) * inverse_edge;
            if (coverage <= 0.0f) continue;
            const float alpha = clamp01(base_deposition * coverage);
            const float deposition = clamp01(1.0f - std::pow(1.0f - alpha, exponent));
            if (deposition <= 0.0f) continue;
            const int index = local_y * stride + x - tile_left;
            const std::uint32_t before = static_cast<std::uint32_t>(destination[index]);
            const float source_alpha = clamp01(reservoir_alpha * deposition);
            const float inverse = 1.0f - source_alpha;
            const float destination_alpha = static_cast<float>((before >> 24) & 0xff) / 255.0f;
            const float destination_red = static_cast<float>((before >> 16) & 0xff) / 255.0f;
            const float destination_green = static_cast<float>((before >> 8) & 0xff) / 255.0f;
            const float destination_blue = static_cast<float>(before & 0xff) / 255.0f;
            const auto to_byte = [](float value) -> std::uint32_t {
                return static_cast<std::uint32_t>(std::clamp(static_cast<int>(value * 255.0f + 0.5f), 0, 255));
            };
            const float output_alpha = source_alpha + destination_alpha * inverse;
            const float scale = output_alpha > opacity_limit && output_alpha > 0.f ? opacity_limit / output_alpha : 1.f;
            const std::uint32_t after =
                (to_byte(std::min(output_alpha, opacity_limit)) << 24) |
                (to_byte((reservoir_red * deposition + destination_red * inverse) * scale) << 16) |
                (to_byte((reservoir_green * deposition + destination_green * inverse) * scale) << 8) |
                to_byte((reservoir_blue * deposition + destination_blue * inverse) * scale);
            if (after != before) {
                destination[index] = static_cast<jint>(after);
                changed = true;
            }
        }
    }
    env->ReleasePrimitiveArrayCritical(destination_array, destination, 0);
    return changed ? 1 : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeDepositTexturedWet(
    JNIEnv* env,
    jobject,
    jintArray destination_array,
    jint stride,
    jint tile_left,
    jint tile_top,
    jint left,
    jint top,
    jint right,
    jint bottom,
    jfloat center_x,
    jfloat center_y,
    jfloat radius_x,
    jfloat radius_y,
    jfloat cosine,
    jfloat sine,
    jfloat outer_radius,
    jfloat inner_radius,
    jfloat inverse_edge,
    jbyteArray tip_array,
    jint tip_width,
    jint tip_height,
    jbyteArray grain_array,
    jint grain_width,
    jint grain_height,
    jfloat grain_density,
    jfloat grain_cosine,
    jfloat grain_sine,
    jfloat grain_wavelength,
    jfloat grain_movement,
    jfloat grain_phase_x,
    jfloat grain_phase_y,
    jfloat base_deposition,
    jint transfer_mode,
    jfloat reservoir_alpha,
    jfloat reservoir_red,
    jfloat reservoir_green,
    jfloat reservoir_blue,
    jfloat opacity_limit) {
    if (!std::isfinite(opacity_limit) || opacity_limit < 0.f || opacity_limit > 1.f) return -1;
    const bool has_tip = tip_array != nullptr;
    const bool has_grain = grain_array != nullptr;
    if (destination_array == nullptr || stride <= 0 || left < tile_left || top < tile_top ||
        right <= left || bottom <= top || right - tile_left > stride || bottom - tile_top > stride ||
        radius_x <= 0.0f || radius_y <= 0.0f || outer_radius <= 0.0f || inner_radius < 0.0f ||
        inverse_edge <= 0.0f || transfer_mode < 0 || transfer_mode > 5 ||
        (has_tip && (tip_width <= 0 || tip_height <= 0 ||
            env->GetArrayLength(tip_array) < static_cast<std::int64_t>(tip_width) * tip_height)) ||
        (has_grain && (grain_width <= 0 || grain_height <= 0 || grain_wavelength <= 0.0f ||
            env->GetArrayLength(grain_array) < static_cast<std::int64_t>(grain_width) * grain_height))) return -1;
    if (env->GetArrayLength(destination_array) < stride * stride) return -1;

    auto* destination = static_cast<jint*>(env->GetPrimitiveArrayCritical(destination_array, nullptr));
    if (destination == nullptr) return -1;
    auto* tip = has_tip ? static_cast<jbyte*>(env->GetPrimitiveArrayCritical(tip_array, nullptr)) : nullptr;
    if (has_tip && tip == nullptr) {
        env->ReleasePrimitiveArrayCritical(destination_array, destination, JNI_ABORT);
        return -1;
    }
    auto* grain = has_grain ? static_cast<jbyte*>(env->GetPrimitiveArrayCritical(grain_array, nullptr)) : nullptr;
    if (has_grain && grain == nullptr) {
        if (tip != nullptr) env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(destination_array, destination, JNI_ABORT);
        return -1;
    }

    static constexpr float exponents[] = {0.65f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    const float exponent = exponents[transfer_mode];
    const float outer_squared = outer_radius * outer_radius;
    const float inner_squared = inner_radius * inner_radius;
    bool changed = false;
    for (int y = top; y < bottom; ++y) {
        const int local_y = y - tile_top;
        for (int x = left; x < right; ++x) {
            const float dx = x + 0.5f - center_x;
            const float dy = y + 0.5f - center_y;
            float coverage;
            if (has_tip) {
                const float local_x = cosine * dx + sine * dy;
                const float local_y = -sine * dx + cosine * dy;
                const float u = (local_x / radius_x + 1.0f) * 0.5f;
                const float v = (local_y / radius_y + 1.0f) * 0.5f;
                if (radius_x >= 1.0f && radius_y >= 1.0f) {
                    coverage = sample_alpha_mask(tip, tip_width, tip_height, u, v);
                } else {
                    const float footprint = std::abs(cosine) + std::abs(sine);
                    auto overlap = [footprint](float position, float radius) {
                        return std::max(0.0f, std::min(position + footprint * 0.5f, radius) -
                            std::max(position - footprint * 0.5f, -radius)) / footprint;
                    };
                    const float area = overlap(local_x, radius_x) * overlap(local_y, radius_y);
                    coverage = area <= 0.0f ? 0.0f : sample_alpha_mask(tip, tip_width, tip_height,
                        std::clamp(u, 0.0f, 1.0f), std::clamp(v, 0.0f, 1.0f)) * area;
                }
            } else {
                const float distance_squared = dx * dx + dy * dy;
                if (distance_squared >= outer_squared) continue;
                coverage = distance_squared <= inner_squared ? 1.0f :
                    (outer_radius - std::sqrt(distance_squared)) * inverse_edge;
            }
            if (coverage <= 0.0f) continue;
            if (has_grain) {
                const float grain_x = x + 0.5f - center_x * (1.0f - grain_movement);
                const float grain_y = y + 0.5f - center_y * (1.0f - grain_movement);
                const float rotated_x = grain_cosine * grain_x + grain_sine * grain_y;
                const float rotated_y = -grain_sine * grain_x + grain_cosine * grain_y;
                const float raw_u = rotated_x / grain_wavelength + grain_phase_x;
                const float raw_v = rotated_y / grain_wavelength + grain_phase_y;
                const float sampled = sample_alpha_mask(
                    grain, grain_width, grain_height, raw_u - std::floor(raw_u), raw_v - std::floor(raw_v));
                coverage *= 1.0f - grain_density + sampled * grain_density;
            }
            if (coverage <= 0.0f) continue;
            const float alpha = clamp01(base_deposition * coverage);
            const float deposition = clamp01(1.0f - std::pow(1.0f - alpha, exponent));
            if (deposition <= 0.0f) continue;
            const int index = local_y * stride + x - tile_left;
            const std::uint32_t before = static_cast<std::uint32_t>(destination[index]);
            const float source_alpha = clamp01(reservoir_alpha * deposition);
            const float inverse = 1.0f - source_alpha;
            const auto to_byte = [](float value) -> std::uint32_t {
                return static_cast<std::uint32_t>(std::clamp(static_cast<int>(value * 255.0f + 0.5f), 0, 255));
            };
            const float output_alpha = source_alpha + ((before >> 24) & 0xff) / 255.0f * inverse;
            const float scale = output_alpha > opacity_limit && output_alpha > 0.f ? opacity_limit / output_alpha : 1.f;
            const std::uint32_t after =
                (to_byte(std::min(output_alpha, opacity_limit)) << 24) |
                (to_byte((reservoir_red * deposition + ((before >> 16) & 0xff) / 255.0f * inverse) * scale) << 16) |
                (to_byte((reservoir_green * deposition + ((before >> 8) & 0xff) / 255.0f * inverse) * scale) << 8) |
                to_byte((reservoir_blue * deposition + (before & 0xff) / 255.0f * inverse) * scale);
            if (after != before) {
                destination[index] = static_cast<jint>(after);
                changed = true;
            }
        }
    }
    if (grain != nullptr) env->ReleasePrimitiveArrayCritical(grain_array, grain, JNI_ABORT);
    if (tip != nullptr) env->ReleasePrimitiveArrayCritical(tip_array, tip, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(destination_array, destination, 0);
    return changed ? 1 : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeUnpremultiplyInPlace(
    JNIEnv* env,
    jobject,
    jintArray pixels_array) {
    if (pixels_array == nullptr) return JNI_FALSE;
    const jsize size = env->GetArrayLength(pixels_array);
    auto* pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(pixels_array, nullptr));
    if (pixels == nullptr) return JNI_FALSE;
    for (jsize index = 0; index < size; ++index) {
        const std::uint32_t pixel = static_cast<std::uint32_t>(pixels[index]);
        const int alpha = static_cast<int>(pixel >> 24);
        if (alpha == 0 || alpha == 255) continue;
        const int red = std::min(255, (static_cast<int>((pixel >> 16) & 0xff) * 255 + alpha / 2) / alpha);
        const int green = std::min(255, (static_cast<int>((pixel >> 8) & 0xff) * 255 + alpha / 2) / alpha);
        const int blue = std::min(255, (static_cast<int>(pixel & 0xff) * 255 + alpha / 2) / alpha);
        pixels[index] = static_cast<jint>((alpha << 24) | (red << 16) | (green << 8) | blue);
    }
    env->ReleasePrimitiveArrayCritical(pixels_array, pixels, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativePremultiplyInPlace(
    JNIEnv* env, jobject, jintArray pixels_array) {
    if (pixels_array == nullptr) return JNI_FALSE;
    const jsize size = env->GetArrayLength(pixels_array);
    auto* pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(pixels_array, nullptr));
    if (pixels == nullptr) return JNI_FALSE;
    for (jsize index = 0; index < size; ++index) {
        const std::uint32_t pixel = static_cast<std::uint32_t>(pixels[index]);
        const std::uint32_t alpha = pixel >> 24;
        if (alpha == 0 || alpha == 255) continue;
        const auto red = (((pixel >> 16) & 255) * alpha + 127) / 255;
        const auto green = (((pixel >> 8) & 255) * alpha + 127) / 255;
        const auto blue = ((pixel & 255) * alpha + 127) / 255;
        pixels[index] = static_cast<jint>((alpha << 24) | (red << 16) | (green << 8) | blue);
    }
    env->ReleasePrimitiveArrayCritical(pixels_array, pixels, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeNonTransparentPixelCount(
    JNIEnv* env,
    jobject,
    jintArray pixels_array) {
    if (pixels_array == nullptr) return -1;
    const jsize size = env->GetArrayLength(pixels_array);
    auto* pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(pixels_array, nullptr));
    if (pixels == nullptr) return -1;
    int count = 0;
    for (jsize index = 0; index < size; ++index) {
        count += static_cast<std::uint32_t>(pixels[index]) >> 24 != 0;
    }
    env->ReleasePrimitiveArrayCritical(pixels_array, pixels, JNI_ABORT);
    return count;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeHashPremultipliedPixels(
    JNIEnv* env,
    jobject,
    jintArray pixels_array) {
    if (pixels_array == nullptr) return 0;
    const jsize size = env->GetArrayLength(pixels_array);
    auto* pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(pixels_array, nullptr));
    if (pixels == nullptr) return 0;
    std::uint64_t hash = 14695981039346656037ULL;
    for (jsize index = 0; index < size; ++index) {
        const std::uint32_t pixel = static_cast<std::uint32_t>(pixels[index]);
        for (int shift = 24; shift >= 0; shift -= 8) {
            hash ^= (pixel >> shift) & 0xff;
            hash *= 1099511628211ULL;
        }
    }
    env->ReleasePrimitiveArrayCritical(pixels_array, pixels, JNI_ABORT);
    return static_cast<jlong>(hash);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeCompositePigment(
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
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeBlur(
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
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeAlphaBounds(
    JNIEnv* env, jobject, jobject source_bitmap) {
    LockedBitmap source(env, source_bitmap);
    if (source.pixels == nullptr) return bounds_array(env, {-1, -1, -1, -1});
    return bounds_array(env, alpha_bounds(source));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeIsAlphaEmpty(
    JNIEnv* env, jobject, jobject source_bitmap) {
    LockedBitmap source(env, source_bitmap);
    if (source.pixels == nullptr) return JNI_TRUE;
    for (std::uint32_t y = 0; y < source.info.height; ++y) {
        const auto* row = source.pixels + static_cast<std::size_t>(y) * source.info.stride;
        for (std::uint32_t x = 0; x < source.info.width; ++x) if (row[x * 4 + 3] != 0) return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeNonEmptyAlphaTiles(
    JNIEnv* env, jobject, jobject source_bitmap, jint tile_size_value) {
    LockedBitmap source(env, source_bitmap);
    const int tile_size = static_cast<int>(tile_size_value);
    if (source.pixels == nullptr || tile_size <= 0) return env->NewIntArray(0);
    const int width = static_cast<int>(source.info.width);
    const int height = static_cast<int>(source.info.height);
    const int column_count = (width + tile_size - 1) / tile_size;
    const int row_count = (height + tile_size - 1) / tile_size;
    const std::int64_t tile_count_value = static_cast<std::int64_t>(column_count) * row_count;
    if (tile_count_value <= 0 || tile_count_value > std::numeric_limits<jsize>::max() / 2) {
        return env->NewIntArray(0);
    }
    const std::size_t tile_count = static_cast<std::size_t>(tile_count_value);
    std::vector<std::uint8_t> occupied(tile_count, 0);
    for (int y = 0; y < height; ++y) {
        const auto* row = source.pixels + static_cast<std::size_t>(y) * source.info.stride;
        const int tile_row = y / tile_size;
        for (int x = 0; x < width; ++x) {
            if (row[x * 4 + 3] == 0) continue;
            occupied[static_cast<std::size_t>(tile_row) * column_count + x / tile_size] = 1;
        }
    }
    std::vector<jint> coordinates;
    coordinates.reserve(tile_count * 2);
    for (int row = 0; row < row_count; ++row) {
        for (int column = 0; column < column_count; ++column) {
            if (!occupied[static_cast<std::size_t>(row) * column_count + column]) continue;
            coordinates.push_back(column);
            coordinates.push_back(row);
        }
    }
    jintArray result = env->NewIntArray(static_cast<jsize>(coordinates.size()));
    if (result != nullptr && !coordinates.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(coordinates.size()), coordinates.data());
    }
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeInvertAlpha(
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
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeBoundaryAlpha(
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
        // Selection feedback is a display boundary, not a morphology operation. Keep exactly the
        // one-pixel inner edge; expanding every transition to a 3x3 block produced a 4-6 px halo
        // after scaling and made the marching ants obscure fine artwork.
        if (!inside || (selected(x - 1, y) && selected(x + 1, y) &&
            selected(x, y - 1) && selected(x, y + 1))) continue;
        auto* pixel = destination.pixels + static_cast<std::size_t>(y) * destination.info.stride + x * 4;
        std::fill_n(pixel, 4, 255);
        non_empty = true;
    }
    return non_empty ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeMorphAlpha(
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

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeApplySelectionAlpha(
    JNIEnv* env,
    jobject,
    jobject target_bitmap,
    jobject selection_bitmap,
    jint global_left_value,
    jint global_top_value,
    jint content_width_value,
    jint content_height_value,
    jint surface_width_value,
    jint surface_height_value) {
    LockedBitmap target(env, target_bitmap);
    LockedBitmap selection(env, selection_bitmap);
    if (target.pixels == nullptr || selection.pixels == nullptr) return JNI_FALSE;
    const int surface_width = static_cast<int>(surface_width_value);
    const int surface_height = static_cast<int>(surface_height_value);
    if (surface_width <= 0 || surface_height <= 0 || selection.info.width == 0 || selection.info.height == 0) {
        return JNI_FALSE;
    }
    const int content_width = std::clamp(static_cast<int>(content_width_value), 0, static_cast<int>(target.info.width));
    const int content_height = std::clamp(static_cast<int>(content_height_value), 0, static_cast<int>(target.info.height));
    const int global_left = static_cast<int>(global_left_value);
    const int global_top = static_cast<int>(global_top_value);
    for (int local_y = 0; local_y < content_height; ++local_y) {
        auto* target_row = target.pixels + static_cast<std::size_t>(local_y) * target.info.stride;
        const int global_y = global_top + local_y;
        const std::uint8_t* selection_row = nullptr;
        if (global_y >= 0 && global_y < surface_height) {
            const int selection_y = std::min(
                static_cast<int>(selection.info.height) - 1,
                static_cast<int>(static_cast<std::int64_t>(global_y) * selection.info.height / surface_height));
            selection_row = selection.pixels + static_cast<std::size_t>(selection_y) * selection.info.stride;
        }
        for (int local_x = 0; local_x < content_width; ++local_x) {
            auto* target_pixel = target_row + local_x * 4;
            const int global_x = global_left + local_x;
            std::uint8_t selection_alpha = 0;
            if (selection_row != nullptr && global_x >= 0 && global_x < surface_width) {
                const int selection_x = std::min(
                    static_cast<int>(selection.info.width) - 1,
                    static_cast<int>(static_cast<std::int64_t>(global_x) * selection.info.width / surface_width));
                selection_alpha = selection_row[selection_x * 4 + 3];
            }
            for (int channel = 0; channel < 4; ++channel) {
                target_pixel[channel] = static_cast<std::uint8_t>(
                    (static_cast<std::uint32_t>(target_pixel[channel]) * selection_alpha + 127u) / 255u);
            }
        }
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeFloodFill(
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

    stack.clear();
    if (stack.capacity() < std::min<std::size_t>(count, 4096)) stack.reserve(std::min<std::size_t>(count, 4096));
    auto push = [&](int x, int y) {
        const int index = y * width + x;
        if (fillable[index] != 1) return;
        stack.push_back(index);
        fillable[index] = 2;
    };
    push(start_x, start_y);
    while (!stack.empty()) {
        const int index = stack.back();
        stack.pop_back();
        if (fillable[index] == 3) continue;
        const int y = index / width;
        int x = index % width;
        while (x >= 0 && fillable[y * width + x] != 0 && fillable[y * width + x] != 3) --x;
        ++x;
        bool span_up = false;
        bool span_down = false;
        while (x < width && fillable[y * width + x] != 0 && fillable[y * width + x] != 3) {
            fillable[y * width + x] = 3;
            if (y > 0) {
                const bool candidate = fillable[(y - 1) * width + x] == 1;
                if (!span_up && candidate) { push(x, y - 1); span_up = true; }
                else if (span_up && !candidate) span_up = false;
            }
            if (y + 1 < height) {
                const bool candidate = fillable[(y + 1) * width + x] == 1;
                if (!span_down && candidate) { push(x, y + 1); span_down = true; }
                else if (span_down && !candidate) span_down = false;
            }
            ++x;
        }
    }
    const int scaling = std::clamp(static_cast<int>(area_scaling_value), -64, 64);
    if (scaling != 0) {
        covered.resize(count);
        for (std::size_t index = 0; index < count; ++index) covered[index] = fillable[index] == 3;
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
            const std::size_t index = static_cast<std::size_t>(y) * width + x;
            const bool selected = scaling == 0 ? fillable[index] == 3 : covered[index] != 0;
            const std::uint8_t value = selected ? 255 : 0;
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


extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeTranslatedSelectionAlpha(
    JNIEnv* env, jobject, jbyteArray input_array, jint stride, jbyteArray output_array,
    jint width, jint height, jfloatArray weights_x, jfloatArray weights_y) {
    if (!input_array || !output_array || !weights_x || !weights_y || width <= 0 || height <= 0) return -1;
    const int count = env->GetArrayLength(weights_x);
    if ((count != 2 && count != 4) || env->GetArrayLength(weights_y) != count || stride < width + count - 1 ||
        static_cast<std::int64_t>(stride) * (height + count - 1) > env->GetArrayLength(input_array) ||
        static_cast<std::int64_t>(width) * height > env->GetArrayLength(output_array)) return -1;
    float wx[4] = {}, wy[4] = {};
    env->GetFloatArrayRegion(weights_x, 0, count, wx);
    env->GetFloatArrayRegion(weights_y, 0, count, wy);
    auto* input = env->GetByteArrayElements(input_array, nullptr);
    if (!input) return -1;
    auto* output = env->GetByteArrayElements(output_array, nullptr);
    if (!output) { env->ReleaseByteArrayElements(input_array, input, JNI_ABORT); return -1; }
    int nonzero = 0;
    for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
        float alpha = 0;
        for (int row = 0; row < count; ++row) {
            if (wy[row] == 0) continue;
            const auto offset = static_cast<std::size_t>(y + row) * stride + x;
            for (int col = 0; col < count; ++col)
                alpha += static_cast<std::uint8_t>(input[offset + col]) * wx[col] * wy[row];
        }
        const auto value = std::clamp(static_cast<int>(std::floor(alpha + .5f)), 0, 255);
        output[static_cast<std::size_t>(y) * width + x] = static_cast<jbyte>(value);
        nonzero += value != 0;
    }
    env->ReleaseByteArrayElements(input_array, input, JNI_ABORT);
    env->ReleaseByteArrayElements(output_array, output, 0);
    return nonzero;
}

extern "C" JNIEXPORT jint JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeAffineSelectionAlpha(
    JNIEnv* env, jobject, jbyteArray input_array, jint source_left, jint source_top, jint source_width, jint source_height,
    jbyteArray output_array, jint left, jint top, jint width, jint height, jfloatArray matrix_array, jint interpolation) {
#pragma clang fp contract(off)
    if (!input_array || !output_array || !matrix_array || source_width <= 0 || source_height <= 0 || width <= 0 || height <= 0 ||
        interpolation < 0 || interpolation > 2 || (env->GetArrayLength(matrix_array) != 6 && env->GetArrayLength(matrix_array) != 9) ||
        static_cast<std::int64_t>(source_width) * source_height > env->GetArrayLength(input_array) ||
        static_cast<std::int64_t>(width) * height > env->GetArrayLength(output_array)) return -1;
    float m[9] = {0,0,0,0,0,0,0,0,1}; env->GetFloatArrayRegion(matrix_array, 0, env->GetArrayLength(matrix_array), m);
    auto* input = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(input_array, nullptr));
    if (!input) return -1;
    auto* output = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(output_array, nullptr));
    if (!output) { env->ReleasePrimitiveArrayCritical(input_array, input, JNI_ABORT); return -1; }
    auto at = [&](int x, int y) -> float {
        return x < 0 || y < 0 || x >= source_width || y >= source_height ? 0.f :
            static_cast<std::uint8_t>(input[static_cast<std::size_t>(y) * source_width + x]);
    };
    auto cubic = [](float v) { const float x = std::abs(v); return x < 1 ? 1.5f*x*x*x - 2.5f*x*x + 1 : x < 2 ? -.5f*x*x*x + 2.5f*x*x - 4*x + 2 : 0; };
    auto sample = [&](float x, float y) -> float {
        if (!std::isfinite(x) || !std::isfinite(y) || x < -2 || y < -2 || x > source_width + 2 || y > source_height + 2) return 0;
        if (interpolation == 0) return at(static_cast<int>(std::floor(x)), static_cast<int>(std::floor(y)));
        const float px = x - .5f, py = y - .5f;
        const int x0 = static_cast<int>(std::floor(px)), y0 = static_cast<int>(std::floor(py));
        const float fx = px - x0, fy = py - y0;
        const int first = interpolation == 2 ? -1 : 0, count = interpolation == 2 ? 4 : 2;
        float sum = 0;
        for (int row = 0; row < count; ++row) for (int col = 0; col < count; ++col) {
            const float weight = interpolation == 2 ? cubic(fx - col + 1) * cubic(fy - row + 1) :
                (col == 0 ? 1-fx : fx) * (row == 0 ? 1-fy : fy);
            if (weight != 0) sum += at(x0 + first + col, y0 + first + row) * weight;
        }
        return sum;
    };
    int non_zero = 0;
    for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
        const float gx = left + x + .5f, gy = top + y + .5f;
        const float value = sample((m[0]*gx + m[1]*gy + m[4]) / (m[6]*gx + m[7]*gy + m[8]) - source_left, (m[2]*gx + m[3]*gy + m[5]) / (m[6]*gx + m[7]*gy + m[8]) - source_top);
        const auto alpha = std::clamp(static_cast<int>(std::floor(value + .5f)), 0, 255);
        output[static_cast<std::size_t>(y) * width + x] = static_cast<jbyte>(alpha);
        if (alpha != 0) ++non_zero;
    }
    env->ReleasePrimitiveArrayCritical(input_array, input, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(output_array, output, 0);
    return non_zero;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeSamplePigmentBuckets(
    JNIEnv* env, jobject, jintArray pixels_array, jint width, jint height,
    jint columns, jint rows, jfloatArray output_array) {
#pragma clang fp contract(off)
    if (!pixels_array || !output_array || width <= 0 || height <= 0 || columns < 2 || columns > 32 || rows < 2 || rows > 32 ||
        static_cast<std::int64_t>(width) * height > env->GetArrayLength(pixels_array) ||
        columns * rows * 4 > env->GetArrayLength(output_array)) return JNI_FALSE;
    auto* output = env->GetFloatArrayElements(output_array, nullptr);
    if (!output) return JNI_FALSE;
    auto* pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(pixels_array, nullptr));
    if (!pixels) { env->ReleaseFloatArrayElements(output_array, output, JNI_ABORT); return JNI_FALSE; }
    for (int row = 0; row < rows; ++row) {
        const int top = std::clamp(static_cast<int>(std::floor(row * static_cast<float>(height) / rows)), 0, height - 1);
        const int bottom = std::clamp(static_cast<int>(std::ceil((row + 1) * static_cast<float>(height) / rows)), top + 1, height);
        for (int col = 0; col < columns; ++col) {
            const int left = std::clamp(static_cast<int>(std::floor(col * static_cast<float>(width) / columns)), 0, width - 1);
            const int right = std::clamp(static_cast<int>(std::ceil((col + 1) * static_cast<float>(width) / columns)), left + 1, width);
            float a = 0, r = 0, g = 0, b = 0;
            for (int y = top; y < bottom; ++y) for (int x = left; x < right; ++x) {
                const auto color = static_cast<std::uint32_t>(pixels[static_cast<std::size_t>(y) * width + x]);
                const float alpha = (color >> 24) / 255.f;
                a += alpha;
                r += ((color >> 16) & 255) / 255.f * alpha;
                g += ((color >> 8) & 255) / 255.f * alpha;
                b += (color & 255) / 255.f * alpha;
            }
            const float scale = 1.f / ((bottom - top) * (right - left));
            const int index = (row * columns + col) * 4;
            output[index] = r * scale; output[index + 1] = g * scale;
            output[index + 2] = b * scale; output[index + 3] = a * scale;
        }
    }
    env->ReleasePrimitiveArrayCritical(pixels_array, pixels, JNI_ABORT);
    env->ReleaseFloatArrayElements(output_array, output, 0);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeWritePremultipliedBitmap(
    JNIEnv* env, jobject, jobject bitmap, jintArray pixels_array) {
    if (!bitmap || !pixels_array) return JNI_FALSE;
    const auto count = env->GetArrayLength(pixels_array);
    LockedBitmap target(env, bitmap);
    if (!target.pixels || target.info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        static_cast<std::uint64_t>(target.info.width) * target.info.height > static_cast<std::uint64_t>(count)) return JNI_FALSE;
    const auto* source = static_cast<const jint*>(env->GetPrimitiveArrayCritical(pixels_array, nullptr));
    if (!source) return JNI_FALSE;
    for (std::uint32_t y = 0; y < target.info.height; ++y) {
        auto* row = reinterpret_cast<std::uint32_t*>(target.pixels + y * target.info.stride);
        for (std::uint32_t x = 0; x < target.info.width; ++x) {
            const auto color = static_cast<std::uint32_t>(source[y * target.info.width + x]);
            const auto a = color >> 24;
            auto channel = [a](std::uint32_t value) {
                if (a == 0) return 0u;
                if (a == 255) return value;
                const auto straight = std::min(255u, (value * 255 + a / 2) / a);
                return (straight * a + 127) / 255;
            };
            row[x] = (a << 24) | (channel(color & 255) << 16) |
                (channel((color >> 8) & 255) << 8) | channel((color >> 16) & 255);
        }
    }
    env->ReleasePrimitiveArrayCritical(pixels_array, const_cast<jint*>(source), JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v5_tools_NativeRasterOps_nativeCopyPixelRows(
    JNIEnv* env, jobject, jintArray source_array, jintArray destination_array,
    jint source_offset, jint destination_offset, jint width, jint height,
    jint source_stride, jint destination_stride) {
    if (!source_array || !destination_array || env->IsSameObject(source_array, destination_array) ||
        source_offset < 0 || destination_offset < 0 || width <= 0 || height <= 0 ||
        source_stride < width || destination_stride < width) return JNI_FALSE;
    const auto source_end = static_cast<std::int64_t>(source_offset) +
        static_cast<std::int64_t>(height - 1) * source_stride + width;
    const auto destination_end = static_cast<std::int64_t>(destination_offset) +
        static_cast<std::int64_t>(height - 1) * destination_stride + width;
    if (source_end > env->GetArrayLength(source_array) ||
        destination_end > env->GetArrayLength(destination_array)) return JNI_FALSE;
    const auto* source = static_cast<const jint*>(env->GetPrimitiveArrayCritical(source_array, nullptr));
    if (!source) return JNI_FALSE;
    auto* destination = static_cast<jint*>(env->GetPrimitiveArrayCritical(destination_array, nullptr));
    if (!destination) {
        env->ReleasePrimitiveArrayCritical(source_array, const_cast<jint*>(source), JNI_ABORT);
        return JNI_FALSE;
    }
    for (int y = 0; y < height; ++y) {
        std::memcpy(destination + destination_offset + static_cast<std::size_t>(y) * destination_stride,
            source + source_offset + static_cast<std::size_t>(y) * source_stride,
            static_cast<std::size_t>(width) * sizeof(jint));
    }
    env->ReleasePrimitiveArrayCritical(destination_array, destination, 0);
    env->ReleasePrimitiveArrayCritical(source_array, const_cast<jint*>(source), JNI_ABORT);
    return JNI_TRUE;
}
