#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <jni.h>

#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef MFD_ALLOW_SEALING
#define MFD_ALLOW_SEALING 2U
#endif

#ifndef F_ADD_SEALS
#define F_ADD_SEALS 1033
#define F_SEAL_SHRINK 0x0002
#define F_SEAL_GROW   0x0004
#define F_SEAL_WRITE  0x0008
#endif

namespace {

void throwIOException(JNIEnv* env, const std::string& message) {
    jclass exceptionClass = env->FindClass("java/io/IOException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

std::string errnoMessage(const char* prefix) {
    return std::string(prefix) + ": " + std::strerror(errno);
}

int createMemfd(const char* name) {
#ifdef __NR_memfd_create
    return static_cast<int>(syscall(__NR_memfd_create, name, MFD_ALLOW_SEALING));
#else
    errno = ENOSYS;
    return -1;
#endif
}

bool writeAll(int fd, const void* data, size_t size) {
    const auto* cursor = static_cast<const unsigned char*>(data);
    size_t remaining = size;
    while (remaining > 0) {
        const ssize_t written = write(fd, cursor, remaining);
        if (written < 0) {
            if (errno == EINTR) {
                continue;
            }
            return false;
        }
        cursor += written;
        remaining -= static_cast<size_t>(written);
    }
    return true;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_lumus_vkapp_transport_VkTurnExecutableLoader_stageMemfd(
    JNIEnv* env,
    jclass,
    jobject assetManager,
    jstring assetPath
) {
    if (assetManager == nullptr || assetPath == nullptr) {
        throwIOException(env, "vkturn asset manager is unavailable");
        return -1;
    }

    AAssetManager* nativeAssetManager = AAssetManager_fromJava(env, assetManager);
    if (nativeAssetManager == nullptr) {
        throwIOException(env, "Failed to access AssetManager");
        return -1;
    }

    const char* assetPathChars = env->GetStringUTFChars(assetPath, nullptr);
    if (assetPathChars == nullptr) {
        return -1;
    }

    AAsset* asset = AAssetManager_open(nativeAssetManager, assetPathChars, AASSET_MODE_STREAMING);
    env->ReleaseStringUTFChars(assetPath, assetPathChars);
    if (asset == nullptr) {
        throwIOException(env, "Bundled vk-turn binary asset was not found");
        return -1;
    }

    const off64_t assetSize = AAsset_getLength64(asset);
    if (assetSize <= 0) {
        AAsset_close(asset);
        throwIOException(env, "Bundled vk-turn binary asset is empty");
        return -1;
    }

    int fd = createMemfd("vkturn");
    if (fd < 0) {
        AAsset_close(asset);
        throwIOException(env, errnoMessage("memfd_create failed"));
        return -1;
    }

    if (ftruncate(fd, assetSize) != 0) {
        const std::string message = errnoMessage("ftruncate failed");
        close(fd);
        AAsset_close(asset);
        throwIOException(env, message);
        return -1;
    }

    unsigned char buffer[64 * 1024];
    while (true) {
        const int bytesRead = AAsset_read(asset, buffer, sizeof(buffer));
        if (bytesRead < 0) {
            const std::string message = errnoMessage("Reading vk-turn asset failed");
            close(fd);
            AAsset_close(asset);
            throwIOException(env, message);
            return -1;
        }
        if (bytesRead == 0) {
            break;
        }
        if (!writeAll(fd, buffer, static_cast<size_t>(bytesRead))) {
            const std::string message = errnoMessage("Writing memfd failed");
            close(fd);
            AAsset_close(asset);
            throwIOException(env, message);
            return -1;
        }
    }

    AAsset_close(asset);

    if (lseek(fd, 0, SEEK_SET) < 0) {
        const std::string message = errnoMessage("lseek failed");
        close(fd);
        throwIOException(env, message);
        return -1;
    }

    // Android 10+ (W^X policy) blocks fchmod with execute on a writable memfd.
    // Sealing write access first makes the file immutable, allowing the kernel
    // to grant the execute bit without violating W^X.
    if (fcntl(fd, F_ADD_SEALS, F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK) != 0) {
        const std::string message = errnoMessage("fcntl F_ADD_SEALS failed");
        close(fd);
        throwIOException(env, message);
        return -1;
    }

    if (fchmod(fd, S_IRUSR | S_IXUSR) != 0) {
        const std::string message = errnoMessage("fchmod failed");
        close(fd);
        throwIOException(env, message);
        return -1;
    }

    const int existingFlags = fcntl(fd, F_GETFD);
    if (existingFlags < 0 || fcntl(fd, F_SETFD, existingFlags & ~FD_CLOEXEC) != 0) {
        const std::string message = errnoMessage("fcntl failed");
        close(fd);
        throwIOException(env, message);
        return -1;
    }

    return fd;
}

extern "C" JNIEXPORT void JNICALL
Java_com_lumus_vkapp_transport_VkTurnExecutableLoader_closeFd(
    JNIEnv*,
    jclass,
    jint fd
) {
    if (fd >= 0) {
        close(fd);
    }
}
