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

}  // namespace

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
