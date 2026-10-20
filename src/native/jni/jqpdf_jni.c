/* jqpdf JNI shim: a thin C boundary over qpdf's job C API
 * (qpdfjob-c.h / qpdflogger-c.h).
 *
 * Design constraints:
 *  - The shim never calls back into Java. Output that qpdf would print is
 *    captured into C-side growable buffers owned by each job's native peer;
 *    the Java side drains them through JNI accessor methods after a run.
 *  - Every function takes the peer as an opaque jlong handle; libqpdf is
 *    thread-safe as long as one job is used by one thread at a time, which
 *    the Java API enforces.
 *  - Strings cross the boundary as real UTF-8 byte arrays, not JNI's
 *    modified UTF-8: argv travels as one NUL-separated blob split here, and
 *    captured text is returned as raw bytes that Java decodes as UTF-8.
 */

#include <jni.h>

#include <stdlib.h>
#include <string.h>

#include <qpdf/qpdfjob-c.h>
#include <qpdf/qpdflogger-c.h>

#ifdef _WIN32
# include <windows.h>
typedef CRITICAL_SECTION jqpdf_mutex;
static void mutex_init(jqpdf_mutex* m) { InitializeCriticalSection(m); }
static void mutex_lock(jqpdf_mutex* m) { EnterCriticalSection(m); }
static void mutex_unlock(jqpdf_mutex* m) { LeaveCriticalSection(m); }
#else
# include <pthread.h>
typedef pthread_mutex_t jqpdf_mutex;
static void mutex_init(jqpdf_mutex* m) { pthread_mutex_init(m, NULL); }
static void mutex_lock(jqpdf_mutex* m) { pthread_mutex_lock(m); }
static void mutex_unlock(jqpdf_mutex* m) { pthread_mutex_unlock(m); }
#endif

/* Marshalling contract between this shim and
 * dev.machineminds.jqpdf.QpdfNative. Bump on any incompatible change to the
 * native method set below; both sides must agree. */
#define JQPDF_JNI_ABI 1

/* qpdf's argument parser (used for --version, --help, usage errors, ...)
 * writes through the process-default logger, not the per-job logger. Those
 * destinations are redirected here at library load so the library never
 * writes to the host process's console; each job reports the slice of the
 * global buffers that accumulated while it existed. Concurrent jobs may
 * interleave their parser-level output; it is informational only. */
typedef struct {
    char* info;
    size_t info_len;
    size_t info_cap;
    char* warn;
    size_t warn_len;
    size_t warn_cap;
    char* error;
    size_t error_len;
    size_t error_cap;
    unsigned char* save;
    size_t save_len;
    size_t save_cap;
    jqpdf_mutex mutex;
} jqpdf_global;

/* Statically zero-initialized; the mutex is initialized in JNI_OnLoad. */
static jqpdf_global g;

static int global_capture_info(char const* data, size_t len, void* udata);
static int global_capture_warn(char const* data, size_t len, void* udata);
static int global_capture_error(char const* data, size_t len, void* udata);
static int global_capture_save(char const* data, size_t len, void* udata);

typedef struct {
    qpdfjob_handle job;
    qpdflogger_handle logger;
    char* info;
    size_t info_len;
    size_t info_cap;
    char* warn;
    size_t warn_len;
    size_t warn_cap;
    char* error;
    size_t error_len;
    size_t error_cap;
    unsigned char* save;
    size_t save_len;
    size_t save_cap;
    size_t global_info_mark;
    size_t global_warn_mark;
    size_t global_error_mark;
    size_t global_save_mark;
} jqpdf_peer;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved)
{
    (void)vm;
    (void)reserved;
    mutex_init(&g.mutex);
    qpdflogger_handle logger = qpdflogger_default_logger();
    qpdflogger_set_info(logger, qpdf_log_dest_custom, global_capture_info, &g);
    qpdflogger_set_warn(logger, qpdf_log_dest_custom, global_capture_warn, &g);
    qpdflogger_set_error(logger, qpdf_log_dest_custom, global_capture_error, &g);
    qpdflogger_set_save(logger, qpdf_log_dest_custom, global_capture_save, &g, 0);
    qpdflogger_cleanup(&logger);
    return JNI_VERSION_1_8;
}

static void
throw_oom(JNIEnv* env)
{
    jclass cls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
    if (cls) {
        (*env)->ThrowNew(env, cls, "native allocation failed in jqpdf shim");
    }
}

static void
throw_iae(JNIEnv* env, char const* message)
{
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (cls) {
        (*env)->ThrowNew(env, cls, message);
    }
}

static int
append_bytes(char** buf, size_t* len, size_t* cap, void const* data, size_t n)
{
    if (n == 0) {
        return 0;
    }
    if (*len + n > *cap) {
        size_t new_cap = *cap ? *cap : 4096;
        while (new_cap < *len + n) {
            new_cap *= 2;
        }
        char* grown = realloc(*buf, new_cap);
        if (!grown) {
            return 1;
        }
        *buf = grown;
        *cap = new_cap;
    }
    memcpy(*buf + *len, data, n);
    *len += n;
    return 0;
}

static int
capture_info(char const* data, size_t len, void* udata)
{
    jqpdf_peer* peer = udata;
    return append_bytes(&peer->info, &peer->info_len, &peer->info_cap, data, len);
}

static int
capture_warn(char const* data, size_t len, void* udata)
{
    jqpdf_peer* peer = udata;
    return append_bytes(&peer->warn, &peer->warn_len, &peer->warn_cap, data, len);
}

static int
capture_error(char const* data, size_t len, void* udata)
{
    jqpdf_peer* peer = udata;
    return append_bytes(&peer->error, &peer->error_len, &peer->error_cap, data, len);
}

static int
capture_save(char const* data, size_t len, void* udata)
{
    jqpdf_peer* peer = udata;
    return append_bytes(
        (char**)&peer->save, &peer->save_len, &peer->save_cap, data, len);
}

static int
global_capture_info(char const* data, size_t len, void* udata)
{
    jqpdf_global* g_ = udata;
    mutex_lock(&g_->mutex);
    int rc = append_bytes(&g_->info, &g_->info_len, &g_->info_cap, data, len);
    mutex_unlock(&g_->mutex);
    return rc;
}

static int
global_capture_warn(char const* data, size_t len, void* udata)
{
    jqpdf_global* g_ = udata;
    mutex_lock(&g_->mutex);
    int rc = append_bytes(&g_->warn, &g_->warn_len, &g_->warn_cap, data, len);
    mutex_unlock(&g_->mutex);
    return rc;
}

static int
global_capture_error(char const* data, size_t len, void* udata)
{
    jqpdf_global* g_ = udata;
    mutex_lock(&g_->mutex);
    int rc = append_bytes(&g_->error, &g_->error_len, &g_->error_cap, data, len);
    mutex_unlock(&g_->mutex);
    return rc;
}

static int
global_capture_save(char const* data, size_t len, void* udata)
{
    jqpdf_global* g_ = udata;
    mutex_lock(&g_->mutex);
    int rc = append_bytes(
        (char**)&g_->save, &g_->save_len, &g_->save_cap, data, len);
    mutex_unlock(&g_->mutex);
    return rc;
}

/* Splits a blob of argc NUL-terminated UTF-8 strings into an argv vector.
 * Returns a malloc'd char** whose entries point into *owning_copy, or NULL
 * with a pending exception on failure. Caller frees both. */
static char const**
split_argv_blob(JNIEnv* env, jbyteArray blob, jint argc, char** owning_copy)
{
    jsize blob_len = (*env)->GetArrayLength(env, blob);
    char* buf = malloc((size_t)blob_len + 1);
    char** argv = calloc((size_t)argc + 1, sizeof(char*));
    if (!buf || !argv) {
        free(buf);
        free(argv);
        throw_oom(env);
        return NULL;
    }
    (*env)->GetByteArrayRegion(env, blob, 0, blob_len, (jbyte*)buf);
    if ((*env)->ExceptionCheck(env)) {
        free(buf);
        free(argv);
        return NULL;
    }
    buf[blob_len] = '\0';

    char* p = buf;
    for (jint i = 0; i < argc; ++i) {
        argv[i] = p;
        size_t len = strlen(p);
        p += len + 1;
    }
    if (p != buf + blob_len) {
        free(buf);
        free(argv);
        throw_iae(env, "argv blob does not contain exactly argc NUL-terminated strings");
        return NULL;
    }
    *owning_copy = buf;
    return (char const**)argv;
}

JNIEXPORT jint JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_nativeAbiVersion(
    JNIEnv* env, jobject self)
{
    (void)env;
    (void)self;
    return JQPDF_JNI_ABI;
}

JNIEXPORT jlong JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobCreate(
    JNIEnv* env, jobject self)
{
    (void)self;
    jqpdf_peer* peer = calloc(1, sizeof(jqpdf_peer));
    if (!peer) {
        throw_oom(env);
        return 0;
    }
    peer->job = qpdfjob_init();
    if (!peer->job) {
        free(peer);
        throw_oom(env);
        return 0;
    }
    /* A private logger with buffer destinations keeps each job's text and
     * binary output separate and keeps qpdf from writing to the process
     * stdout/stderr. Must be installed before initialization. */
    peer->logger = qpdflogger_create();
    if (!peer->logger) {
        qpdfjob_cleanup(&peer->job);
        free(peer);
        throw_oom(env);
        return 0;
    }
    qpdflogger_set_info(peer->logger, qpdf_log_dest_custom, capture_info, peer);
    qpdflogger_set_warn(peer->logger, qpdf_log_dest_custom, capture_warn, peer);
    qpdflogger_set_error(peer->logger, qpdf_log_dest_custom, capture_error, peer);
    qpdflogger_set_save(peer->logger, qpdf_log_dest_custom, capture_save, peer, 0);
    qpdfjob_set_logger(peer->job, peer->logger);
    mutex_lock(&g.mutex);
    peer->global_info_mark = g.info_len;
    peer->global_warn_mark = g.warn_len;
    peer->global_error_mark = g.error_len;
    peer->global_save_mark = g.save_len;
    mutex_unlock(&g.mutex);
    return (jlong)(intptr_t)peer;
}

JNIEXPORT void JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobDestroy(
    JNIEnv* env, jobject self, jlong handle)
{
    (void)env;
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (peer) {
        qpdflogger_cleanup(&peer->logger);
        qpdfjob_cleanup(&peer->job);
        free(peer->info);
        free(peer->warn);
        free(peer->error);
        free(peer->save);
        free(peer);
    }
}

JNIEXPORT jint JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobInitializeFromArgv(
    JNIEnv* env, jobject self, jlong handle, jbyteArray argvUtf8, jint argc)
{
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (!peer) {
        throw_iae(env, "job handle is 0");
        return 2;
    }
    char* owning = NULL;
    char const** argv = split_argv_blob(env, argvUtf8, argc, &owning);
    if (!argv) {
        return 2;
    }
    /* argv[0] is the program name, ignored by the option parser. */
    jint rc = qpdfjob_initialize_from_argv(peer->job, argv);
    free(argv);
    free(owning);
    return rc;
}

JNIEXPORT jint JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobInitializeFromJson(
    JNIEnv* env, jobject self, jlong handle, jbyteArray jsonUtf8)
{
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (!peer) {
        throw_iae(env, "job handle is 0");
        return 2;
    }
    jsize len = (*env)->GetArrayLength(env, jsonUtf8);
    char* json = malloc((size_t)len + 1);
    if (!json) {
        throw_oom(env);
        return 2;
    }
    (*env)->GetByteArrayRegion(env, jsonUtf8, 0, len, (jbyte*)json);
    json[len] = '\0';
    jint rc = qpdfjob_initialize_from_json(peer->job, json);
    free(json);
    return rc;
}

JNIEXPORT jint JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobRun(
    JNIEnv* env, jobject self, jlong handle)
{
    (void)env;
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (!peer) {
        throw_iae(env, "job handle is 0");
        return 2;
    }
    return qpdfjob_run(peer->job);
}

/* Builds one byte array from the job's own buffer plus the global buffer
 * slice that accumulated after the job was created (parser-level output
 * such as --version, which qpdf routes through the default logger). The
 * global copy happens under the lock because appending may realloc. */
static jbyteArray
combined_bytes(
    JNIEnv* env,
    void const* job_data,
    size_t job_len,
    char const* global_buf,
    size_t global_mark,
    size_t global_len)
{
    size_t delta = global_len > global_mark ? global_len - global_mark : 0;
    jbyteArray array = (*env)->NewByteArray(env, (jsize)(job_len + delta));
    if (!array) {
        return NULL;
    }
    if (job_len) {
        (*env)->SetByteArrayRegion(env, array, 0, (jsize)job_len, (jbyte*)job_data);
    }
    if (delta) {
        (*env)->SetByteArrayRegion(env, array, (jsize)job_len, (jsize)delta,
                                   (jbyte*)(global_buf + global_mark));
    }
    return array;
}

JNIEXPORT jbyteArray JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobGetInfoText(
    JNIEnv* env, jobject self, jlong handle)
{
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (!peer) {
        throw_iae(env, "job handle is 0");
        return NULL;
    }
    mutex_lock(&g.mutex);
    jbyteArray array = combined_bytes(
        env, peer->info, peer->info_len, g.info, peer->global_info_mark, g.info_len);
    mutex_unlock(&g.mutex);
    return array;
}

JNIEXPORT jbyteArray JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobGetWarnText(
    JNIEnv* env, jobject self, jlong handle)
{
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (!peer) {
        throw_iae(env, "job handle is 0");
        return NULL;
    }
    mutex_lock(&g.mutex);
    jbyteArray array = combined_bytes(
        env, peer->warn, peer->warn_len, g.warn, peer->global_warn_mark, g.warn_len);
    mutex_unlock(&g.mutex);
    return array;
}

JNIEXPORT jbyteArray JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobGetErrorText(
    JNIEnv* env, jobject self, jlong handle)
{
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (!peer) {
        throw_iae(env, "job handle is 0");
        return NULL;
    }
    mutex_lock(&g.mutex);
    jbyteArray array = combined_bytes(
        env, peer->error, peer->error_len, g.error, peer->global_error_mark, g.error_len);
    mutex_unlock(&g.mutex);
    return array;
}

JNIEXPORT jbyteArray JNICALL
Java_dev_machineminds_jqpdf_internal_JniQpdfNative_jobGetSavedData(
    JNIEnv* env, jobject self, jlong handle)
{
    (void)self;
    jqpdf_peer* peer = (jqpdf_peer*)(intptr_t)handle;
    if (!peer) {
        throw_iae(env, "job handle is 0");
        return NULL;
    }
    mutex_lock(&g.mutex);
    jbyteArray array = combined_bytes(
        env, peer->save, peer->save_len, (char const*)g.save,
        peer->global_save_mark, g.save_len);
    mutex_unlock(&g.mutex);
    return array;
}
