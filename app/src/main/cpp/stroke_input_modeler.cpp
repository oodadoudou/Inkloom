#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <new>

namespace {

constexpr float kMinimumPressure = 0.05F;
constexpr float kMinimumDistance = 0.0008F;
constexpr float kVelocityFullScalePxPerMs = 2.0F;
constexpr float kVelocitySmoothing = 0.65F;
constexpr jint kIncreaseWhenSlow = 0;

struct StrokeInputSession {
    float x = 0.0F;
    float y = 0.0F;
    float pressure = 1.0F;
    std::int64_t time_ms = 0;
    float tilt = 0.0F;
    float orientation = 0.0F;
    float velocity = 0.0F;
    bool has_sample = false;

    void reset() noexcept {
        x = 0.0F;
        y = 0.0F;
        pressure = 1.0F;
        time_ms = 0;
        tilt = 0.0F;
        orientation = 0.0F;
        velocity = 0.0F;
        has_sample = false;
    }
};

float clamp01(float value) noexcept {
    return std::clamp(std::isfinite(value) ? value : 0.0F, 0.0F, 1.0F);
}

float normalized_velocity(
    float delta_x,
    float delta_y,
    jint viewport_width,
    jint viewport_height,
    std::int64_t elapsed_ms
) noexcept {
    const float width = static_cast<float>(std::max(viewport_width, 1));
    const float height = static_cast<float>(std::max(viewport_height, 1));
    const float distance = std::hypot(delta_x * width, delta_y * height);
    const float elapsed = static_cast<float>(std::max<std::int64_t>(elapsed_ms, 1));
    return std::clamp(distance / elapsed / kVelocityFullScalePxPerMs, 0.0F, 1.0F);
}

float smoothing(
    float base,
    bool adjust_by_speed,
    jint mode,
    float velocity
) noexcept {
    const float value = std::clamp(std::isfinite(base) ? base : 0.0F, 0.0F, 0.9F);
    if (!adjust_by_speed || value <= 0.0F) return value;
    const float speed = clamp01(velocity);
    if (mode == kIncreaseWhenSlow) {
        return std::clamp(value + (1.0F - speed) * value * 0.6F, 0.0F, 0.9F);
    }
    return std::clamp(value * (1.0F - speed * 0.75F), 0.0F, 0.9F);
}

template <typename T>
void write_value(std::byte* output, std::size_t offset, const T& value) noexcept {
    std::memcpy(output + offset, &value, sizeof(T));
}

void write_output(JNIEnv* env, jobject output_buffer, const StrokeInputSession& session) noexcept {
    auto* output = static_cast<std::byte*>(env->GetDirectBufferAddress(output_buffer));
    const jlong capacity = env->GetDirectBufferCapacity(output_buffer);
    if (output == nullptr || capacity < 40) return;
    write_value(output, 0, session.x);
    write_value(output, 4, session.y);
    write_value(output, 8, session.pressure);
    write_value(output, 16, session.time_ms);
    write_value(output, 24, session.tilt);
    write_value(output, 28, session.orientation);
    write_value(output, 32, session.velocity);
}

StrokeInputSession* from_handle(jlong handle) noexcept {
    return reinterpret_cast<StrokeInputSession*>(static_cast<std::uintptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeCreate(JNIEnv*, jobject) {
    auto* session = new (std::nothrow) StrokeInputSession();
    return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(session));
}

extern "C" JNIEXPORT void JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeDestroy(
    JNIEnv*, jobject, jlong handle
) {
    delete from_handle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeReset(
    JNIEnv*, jobject, jlong handle
) {
    if (auto* session = from_handle(handle); session != nullptr) session->reset();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_inkloom_engine_v2_input_NativeStrokeInputModeler_nativeAppend(
    JNIEnv* env,
    jobject,
    jlong handle,
    jfloat raw_x,
    jfloat raw_y,
    jfloat adjusted_pressure,
    jlong sample_time_ms,
    jfloat sample_tilt,
    jfloat sample_orientation,
    jint viewport_width,
    jint viewport_height,
    jfloat stabilization,
    jboolean stabilization_by_speed,
    jint stabilization_by_speed_mode,
    jboolean force,
    jobject output_buffer
) {
    auto* session = from_handle(handle);
    if (session == nullptr) return JNI_FALSE;
    const float normalized_x = clamp01(raw_x);
    const float normalized_y = clamp01(raw_y);
    const std::int64_t elapsed = session->has_sample
        ? std::max<std::int64_t>(sample_time_ms - session->time_ms, 1)
        : 1;
    const float input_velocity = session->has_sample
        ? normalized_velocity(
            normalized_x - session->x,
            normalized_y - session->y,
            viewport_width,
            viewport_height,
            elapsed
        )
        : 0.0F;
    const float filter = smoothing(
        stabilization,
        stabilization_by_speed == JNI_TRUE,
        stabilization_by_speed_mode,
        input_velocity
    );
    const float modeled_x = session->has_sample
        ? session->x * filter + normalized_x * (1.0F - filter)
        : normalized_x;
    const float modeled_y = session->has_sample
        ? session->y * filter + normalized_y * (1.0F - filter)
        : normalized_y;
    if (force != JNI_TRUE && session->has_sample &&
        std::hypot(modeled_x - session->x, modeled_y - session->y) <= kMinimumDistance) {
        return JNI_FALSE;
    }

    const float raw_velocity = session->has_sample
        ? normalized_velocity(
            modeled_x - session->x,
            modeled_y - session->y,
            viewport_width,
            viewport_height,
            elapsed
        )
        : 0.0F;
    const float modeled_velocity = session->has_sample
        ? session->velocity * kVelocitySmoothing + raw_velocity * (1.0F - kVelocitySmoothing)
        : raw_velocity;
    session->x = clamp01(modeled_x);
    session->y = clamp01(modeled_y);
    session->pressure = std::clamp(
        std::isfinite(adjusted_pressure) ? adjusted_pressure : kMinimumPressure,
        kMinimumPressure,
        1.0F
    );
    session->time_ms = sample_time_ms;
    session->tilt = std::isfinite(sample_tilt) ? sample_tilt : 0.0F;
    session->orientation = std::isfinite(sample_orientation) ? sample_orientation : 0.0F;
    session->velocity = modeled_velocity;
    session->has_sample = true;
    write_output(env, output_buffer, *session);
    return JNI_TRUE;
}
