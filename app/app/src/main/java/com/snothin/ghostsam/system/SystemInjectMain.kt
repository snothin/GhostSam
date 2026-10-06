package com.snothin.ghostsam.system

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File

/** packages.xml injection entrypoint (root app_process; modes dump/check/dry-run/inject/uninstall/restore).
 *  Sequence: inject -> soft reboot (PMS re-reads) -> install companion. Disk write = atomic replace
 *  (OneUI 16 EPERMs in-place writes); backup once before the first write; restore validates first. */
object SystemInjectMain {

    @JvmStatic
    fun main(args: Array<String>) {
        var mode = "dump"
        var keyHex: String? = null
        var apk: String? = null
        var pkg: String? = null
        var xml = PackagesXml.DEFAULT_XML

        val rest = ArrayDeque(args.toList())
        while (rest.isNotEmpty()) {
            when (val arg = rest.removeFirst()) {
                "--mode" -> mode = rest.removeFirstOrNull() ?: mode
                "--keyhex" -> keyHex = rest.removeFirstOrNull()
                "--apk" -> apk = rest.removeFirstOrNull()
                "--pkg" -> pkg = rest.removeFirstOrNull()
                "--xml" -> xml = rest.removeFirstOrNull() ?: xml
                else -> println("[!] unknown arg: $arg")
            }
        }

        try {
            run(mode, xml, keyHex, apk, pkg)
        } catch (t: Throwable) {
            println("[x] $t")
            kotlin.system.exitProcess(1)
        }
    }

    private fun run(mode: String, xml: String, keyHex: String?, apk: String?, pkg: String?) {
        println("[*] ghostsam-inject uid=${android.os.Process.myUid()} mode=$mode xml=$xml")
        when (mode) {
            "dump" -> {
                print(PackagesXml.summarize(File(xml).readBytes()))
            }

            "check" -> {
                val key = resolveKey(keyHex, apk, pkg)
                println("[*] key len=${key.length}")
                println("[check] injected=${PackagesXml.isInjected(PackagesXml.parse(File(xml).readBytes()), key)}")
            }

            "dry-run" -> {
                val key = resolveKey(keyHex, apk, pkg)
                println("[*] key len=${key.length}")
                val patched = PackagesXml.inject(PackagesXml.parse(File(xml).readBytes()), key, ::println)
                println(PackagesXml.verifyInjected(patched, key))
                println("[*] dry-run OK: verified, NOT written (${patched.size} bytes)")
            }

            "inject" -> {
                requireRoot()
                val key = resolveKey(keyHex, apk, pkg)
                println("[*] key len=${key.length}")
                val raw = File(xml).readBytes()
                val patched = PackagesXml.inject(PackagesXml.parse(raw), key, ::println)
                println(PackagesXml.verifyInjected(patched, key))
                backupOnce(xml)
                val backup = File(xml + PackagesXml.BACKUP_SUFFIX).readBytes()
                PackagesXml.parse(backup)
                println("[*] backup verified (parses OK, ${backup.size} bytes)")
                writeAtomic(xml, patched)
                println(PackagesXml.verifyInjected(File(xml).readBytes(), key))
                restorecon(xml)
                println("[+] DONE — soft reboot (kill system_server) BEFORE installing the companion")
                println("[i] rollback anytime: --mode restore   |   key removal: --mode uninstall")
            }

            "uninstall" -> {
                requireRoot()
                val key = resolveKey(keyHex, apk, pkg)
                println("[*] key len=${key.length}")
                val doc = PackagesXml.parse(File(xml).readBytes())
                val removed = PackagesXml.remove(doc, key, ::println)
                if (!removed) {
                    println("[*] nothing to remove (already clean)")
                    return
                }
                val bytes = PackagesXml.toXmlBytes(doc)
                println(PackagesXml.verifyAbsent(bytes, key))
                writeAtomic(xml, bytes)
                println(PackagesXml.verifyAbsent(File(xml).readBytes(), key))
                restorecon(xml)
                File(xml + PackagesXml.BACKUP_SUFFIX).takeIf { it.exists() }?.let {
                    println("[*] backup left untouched: ${it.path}")
                }
                println("[+] DONE — our key removed; soft reboot to apply")
            }

            "restore" -> {
                requireRoot()
                val backup = File(xml + PackagesXml.BACKUP_SUFFIX)
                check(backup.exists()) { "no backup found: ${backup.path}" }
                val bytes = backup.readBytes()
                PackagesXml.structuralCheck(PackagesXml.parse(bytes)) { println(it) }
                writeAtomic(xml, bytes)
                restorecon(xml)
                println("[+] restored $xml from ${backup.path} (${bytes.size} bytes)")
                runCatching {
                    val key = resolveKey(keyHex, apk, pkg)
                    println("[check] injected=${PackagesXml.isInjected(PackagesXml.parse(File(xml).readBytes()), key)}")
                }
                println("[+] DONE — soft reboot to apply")
            }

            else -> {
                println("[x] unknown mode: $mode")
                kotlin.system.exitProcess(2)
            }
        }
    }

    private fun resolveKey(keyHex: String?, apk: String?, pkg: String?): String {
        keyHex?.let { return PackagesXml.normalizeKey(it) }
        apk?.let { path ->
            val info = systemContext().packageManager
                .getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: error("getPackageArchiveInfo failed: $path")
            return certHex(info)
        }
        pkg?.let { name ->
            val info = systemContext().packageManager
                .getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
            return certHex(info)
        }
        error("need one of --keyhex / --apk / --pkg")
    }

    private fun certHex(info: PackageInfo): String {
        val signers = info.signingInfo?.apkContentsSigners
            ?: error("no apkContentsSigners (minSdk>=28 expected)")
        val der = signers.firstOrNull()?.toByteArray() ?: error("empty signers")
        return der.joinToString("") { "%02x".format(it) }
    }

    private fun systemContext(): Context {
        android.os.Looper.prepare()
        val activityThread = Class.forName("android.app.ActivityThread")
        (activityThread.getMethod("currentApplication").invoke(null) as? Context)?.let { return it }
        val thread = activityThread.getMethod("systemMain").invoke(null)
        return activityThread.getMethod("getSystemContext").invoke(thread) as Context
    }

    private fun requireRoot() {
        check(android.os.Process.myUid() == 0) { "root required (uid=${android.os.Process.myUid()})" }
    }

    private fun writeAtomic(xml: String, bytes: ByteArray) {
        val target = File(xml)
        val tmp = File(target.parentFile, target.name + ".gsnew")
        try {
            tmp.outputStream().use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            inheritStat(target, tmp)
            check(tmp.renameTo(target)) { "rename failed: ${tmp.path} -> $xml" }
            println("[*] write OK (atomic replace, ${bytes.size} bytes)")
        } catch (t: Throwable) {
            println("[!] atomic replace failed (${t.message}); fallback direct write")
            tmp.delete()
            target.writeBytes(bytes)
        }
    }

    private fun inheritStat(from: File, to: File) {
        if (!from.exists()) return
        runCatching {
            val owner = execText("/system/bin/stat", "-c", "%U:%G", from.path).trim()
            if (owner.contains(':')) {
                ProcessBuilder("/system/bin/chown", owner, to.path).start().waitFor()
            }
            val mode = execText("/system/bin/stat", "-c", "%a", from.path).trim()
            if (mode.isNotEmpty()) {
                ProcessBuilder("/system/bin/chmod", mode, to.path).start().waitFor()
            }
        }.onFailure { println("[!] inheritStat skipped: $it") }
    }

    private fun execText(vararg cmd: String): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val text = p.inputStream.readBytes().decodeToString()
        p.waitFor()
        return text
    }

    private fun backupOnce(xml: String) {
        val backup = File(xml + PackagesXml.BACKUP_SUFFIX)
        if (backup.exists()) {
            println("[*] backup already exists: ${backup.path}")
            return
        }
        File(xml).copyTo(backup)
        println("[+] backup: ${backup.path}")
    }

    private fun restorecon(xml: String) {
        val rc = try {
            ProcessBuilder("/system/bin/restorecon", xml).inheritIO().start().waitFor()
        } catch (t: Throwable) {
            println("[!] restorecon failed: $t")
            return
        }
        println("[*] restorecon rc=$rc")
    }
}
