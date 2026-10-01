#include <jni.h>
#include <lcms2.h>
#include <vector>
#include <memory>
#include <cstdint>

namespace {
struct Profile { cmsHPROFILE value; explicit Profile(cmsHPROFILE p):value(p){} ~Profile(){if(value)cmsCloseProfile(value);} };
void fail(JNIEnv* env,const char* text){env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),text);}
cmsHPROFILE builtin(int kind) {
    if(kind==0) return cmsCreate_sRGBProfile();
    cmsCIExyY white{.3127,.3290,1.0};
    cmsCIExyYTRIPLE primaries=kind==2 ? cmsCIExyYTRIPLE{{.68,.32,1},{.265,.69,1},{.15,.06,1}} : cmsCIExyYTRIPLE{{.64,.33,1},{.30,.60,1},{.15,.06,1}};
    const double params[]={2.4,1.0/1.055,.055/1.055,1.0/12.92,.04045};
    cmsToneCurve* curve=kind==1 ? cmsBuildGamma(nullptr,1.0) : cmsBuildParametricToneCurve(nullptr,4,params);
    if(!curve) return nullptr;
    cmsToneCurve* curves[]={curve,curve,curve}; auto profile=cmsCreateRGBProfile(&white,&primaries,curves); cmsFreeToneCurve(curve); return profile;
}
cmsHPROFILE open(JNIEnv* env,jbyteArray bytes,int fallback) {
    if(!bytes) return builtin(fallback);
    const auto count=env->GetArrayLength(bytes); if(count<128 || count>16*1024*1024) return nullptr;
    std::vector<unsigned char> data(static_cast<size_t>(count)); env->GetByteArrayRegion(bytes,0,count,reinterpret_cast<jbyte*>(data.data()));
    return cmsOpenProfileFromMem(data.data(),static_cast<cmsUInt32Number>(data.size()));
}
struct Transform { cmsHTRANSFORM value; bool cmyk; ~Transform(){cmsDeleteTransform(value);} };
}
extern "C" JNIEXPORT jint JNICALL Java_app_inkloom_color_NativeColorManagement_profileKind(JNIEnv* env,jobject,jbyteArray bytes) {
    Profile profile(open(env,bytes,0)); if(!profile.value)return 0;
    const auto space=cmsGetColorSpace(profile.value); return space==cmsSigRgbData?1:space==cmsSigCmykData?2:0;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_app_inkloom_color_NativeColorManagement_builtinProfile(JNIEnv* env,jobject,jint kind) {
    if(kind<0 || kind>2){fail(env,"Unknown RGB profile");return nullptr;}
    Profile profile(builtin(kind)); cmsUInt32Number size=0;
    if(!profile.value || !cmsSaveProfileToMem(profile.value,nullptr,&size)){fail(env,"Cannot create RGB profile");return nullptr;}
    std::vector<unsigned char> bytes(size); cmsSaveProfileToMem(profile.value,bytes.data(),&size);
    auto output=env->NewByteArray(static_cast<jsize>(size)); env->SetByteArrayRegion(output,0,static_cast<jsize>(size),reinterpret_cast<jbyte*>(bytes.data())); return output;
}
extern "C" JNIEXPORT jlong JNICALL Java_app_inkloom_color_NativeColorManagement_create(JNIEnv* env,jobject,jbyteArray source,jint sourceBuiltin,jbyteArray destination,jint destinationBuiltin,jbyteArray proof,jint intent,jboolean cmyk) {
    if(intent<0 || intent>3){fail(env,"Invalid color rendering intent");return 0;}
    Profile src(open(env,source,sourceBuiltin)); Profile dst(open(env,destination,destinationBuiltin)); Profile prf(proof?open(env,proof,0):nullptr);
    if(!src.value || !dst.value || cmsGetColorSpace(src.value)!=cmsSigRgbData || (cmyk ? cmsGetColorSpace(dst.value)!=cmsSigCmykData : cmsGetColorSpace(dst.value)!=cmsSigRgbData) || (proof&&!prf.value)) {fail(env,"ICC profiles have incompatible color spaces");return 0;}
    const auto flags=cmsFLAGS_BLACKPOINTCOMPENSATION|(cmyk?0:cmsFLAGS_COPY_ALPHA);
    const auto targetFormat=cmyk?TYPE_CMYK_8:TYPE_RGBA_FLT;
    cmsHTRANSFORM transform=proof ? cmsCreateProofingTransform(src.value,TYPE_RGBA_FLT,dst.value,targetFormat,prf.value,intent,INTENT_RELATIVE_COLORIMETRIC,flags|cmsFLAGS_SOFTPROOFING) : cmsCreateTransform(src.value,TYPE_RGBA_FLT,dst.value,targetFormat,intent,flags);
    if(!transform){fail(env,"ICC transform cannot be constructed");return 0;}
    return reinterpret_cast<jlong>(new Transform{transform,cmyk==JNI_TRUE});
}
extern "C" JNIEXPORT void JNICALL Java_app_inkloom_color_NativeColorManagement_apply(JNIEnv* env,jobject,jlong handle,jfloatArray input,jfloatArray output,jbyteArray cmykOutput) {
    auto* transform=reinterpret_cast<Transform*>(handle);
    if(!transform || !input){fail(env,"Color transform is closed");return;}
    const auto size=env->GetArrayLength(input);
    if(size%4!=0 || size>4*1024*1024 || (transform->cmyk ? (!cmykOutput||env->GetArrayLength(cmykOutput)!=size) : (!output||env->GetArrayLength(output)!=size))) {fail(env,"Invalid color pixel buffer");return;}
    std::vector<float> source(static_cast<size_t>(size)); env->GetFloatArrayRegion(input,0,size,source.data());
    if(transform->cmyk) {std::vector<unsigned char> target(static_cast<size_t>(size)); cmsDoTransform(transform->value,source.data(),target.data(),static_cast<cmsUInt32Number>(size/4)); env->SetByteArrayRegion(cmykOutput,0,size,reinterpret_cast<jbyte*>(target.data()));}
    else {std::vector<float> target(static_cast<size_t>(size)); cmsDoTransform(transform->value,source.data(),target.data(),static_cast<cmsUInt32Number>(size/4)); env->SetFloatArrayRegion(output,0,size,target.data());}
}
extern "C" JNIEXPORT void JNICALL Java_app_inkloom_color_NativeColorManagement_close(JNIEnv*,jobject,jlong handle){delete reinterpret_cast<Transform*>(handle);}
