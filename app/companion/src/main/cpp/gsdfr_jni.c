#define _GNU_SOURCE
#include <jni.h>

#include <errno.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

#include "dfr_core.h"
#include "dfr_kmi.h"
#include "dfr_loader.h"

typedef struct {
  char buf[8192];
  size_t len;
} out_t;

static void out_fmt(out_t *o, const char *fmt, ...) {
  va_list ap;
  int n;

  if (o->len + 1 >= sizeof(o->buf))
    return;
  va_start(ap, fmt);
  n = vsnprintf(o->buf + o->len, sizeof(o->buf) - o->len, fmt, ap);
  va_end(ap);
  if (n > 0) {
    o->len += (size_t)n;
    if (o->len >= sizeof(o->buf))
      o->len = sizeof(o->buf) - 1;
  }
}

static jstring to_jstring(JNIEnv *env, out_t *o) {
  return (*env)->NewStringUTF(env, o->buf);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_companion_GsdfrNative_probe(JNIEnv *env, jclass clazz) {
  out_t o = {{0}, 0};
  char kmi[64];
  struct dfr_ctx ctx;
  int rc;
  (void)clazz;

  if (dfr_kmi_current(kmi, sizeof(kmi)) == 0)
    out_fmt(&o, "kmi=%s\n", kmi);
  else
    out_fmt(&o, "kmi=unknown\n");

  rc = dfr_open(&ctx, DFR_SPI_BASE);
  if (rc) {
    out_fmt(&o, "xfrm_open=%s\n", strerror(-rc));
  } else {
    out_fmt(&o, "xfrm_open=ok\n");
    out_fmt(&o, "sas_in_range=%d\n",
            dfr_count_sas(&ctx, DFR_SPI_BASE, DFR_SPI_BASE + DFR_SPI_SPACE));
    dfr_close(&ctx);
  }
  out_fmt(&o, "uid=%d pid=%d\n", (int)getuid(), (int)getpid());
  return to_jstring(env, &o);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_companion_GsdfrNative_hookCheck(JNIEnv *env,
                                                          jclass clazz) {
  char buf[4096];
  (void)clazz;
  buf[0] = 0;
  dfr_loader_check(buf, sizeof(buf));
  return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_companion_GsdfrNative_hookArm(JNIEnv *env, jclass clazz,
                                                        jstring jkmi,
                                                        jbyteArray jko) {
  char buf[4096];
  const char *kmi = NULL;
  jbyte *ko = NULL;
  jsize n = 0;
  (void)clazz;
  buf[0] = 0;
  if (jkmi != NULL)
    kmi = (*env)->GetStringUTFChars(env, jkmi, NULL);
  if (jko != NULL) {
    n = (*env)->GetArrayLength(env, jko);
    ko = (*env)->GetByteArrayElements(env, jko, NULL);
  }
  if (kmi != NULL && ko != NULL && n > 0)
    dfr_loader_arm(kmi, (const uint8_t *)ko, (size_t)n, buf, sizeof(buf));
  else
    snprintf(buf, sizeof(buf), "status=fail\nnote=bad args\n");
  if (ko != NULL)
    (*env)->ReleaseByteArrayElements(env, jko, ko, JNI_ABORT);
  if (kmi != NULL)
    (*env)->ReleaseStringUTFChars(env, jkmi, kmi);
  return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_companion_GsdfrNative_hookTrigger(JNIEnv *env,
                                                            jclass clazz) {
  char buf[4096];
  (void)clazz;
  buf[0] = 0;
  dfr_loader_trigger(buf, sizeof(buf));
  return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_companion_GsdfrNative_hookFinish(JNIEnv *env,
                                                           jclass clazz,
                                                           jboolean force) {
  char buf[4096];
  (void)clazz;
  buf[0] = 0;
  dfr_loader_finish(force ? 1 : 0, buf, sizeof(buf));
  return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_companion_GsdfrNative_progress(JNIEnv *env,
                                                         jclass clazz,
                                                         jint op) {
  (void)clazz;
  if (op == 0) {
    dfr_loader_progress_clear();
    return (*env)->NewStringUTF(env, "progress=cleared\n");
  }
  {
    char *buf = dfr_loader_progress_dup();
    jstring s = (*env)->NewStringUTF(env, buf ? buf : "");
    free(buf);
    return s;
  }
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_companion_GsdfrNative_kmi(JNIEnv *env, jclass clazz) {
  char buf[64];
  (void)clazz;
  buf[0] = 0;
  if (dfr_kmi_current(buf, sizeof(buf)) != 0)
    buf[0] = 0;
  return (*env)->NewStringUTF(env, buf);
}
