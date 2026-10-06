package com.snothin.ghostsam.system

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser

/** packages.xml pastSigs injection core (root app_process only): writes the companion cert into
 *  android.uid.system's pastSigs so PMS grants uid 1000 on the next framework boot.
 *  PMS read-back constraints: doc-ordered cert table; only the first pastSigs block survives;
 *  newest cert is not a rotation candidate (write two copies); key must be hex; A13+ files are
 *  ABX (parsed via the framework parser; text write-back is safe). */
object PackagesXml {

    const val DEFAULT_XML = "/data/system/packages.xml"
    const val BACKUP_SUFFIX = ".bak-ghostsam-inject"
    const val TARGET_SHARED_USER = "android.uid.system"

    private const val FLAG_SHARED_USER = "2"

    private val INT_ATTRS = setOf(
        "count", "index", "userId", "flags", "schemeVersion", "versionCode", "targetSdk", "minSdk",
    )

    private val HEX_RE = Regex("^[0-9a-f]+$")

    fun isKeyHex(value: String): Boolean =
        value.length > 100 && value.length % 2 == 0 && HEX_RE.matches(value.lowercase())

    fun normalizeKey(value: String): String = value.trim().lowercase()

    fun isAbx(raw: ByteArray): Boolean =
        raw.size >= 4 && raw[0] == 'A'.code.toByte() && raw[1] == 'B'.code.toByte() &&
            raw[2] == 'X'.code.toByte() && raw[3] == 0.toByte()

    fun parse(raw: ByteArray): Document = if (isAbx(raw)) parsePull(raw) else parseText(raw)

    private fun parseText(raw: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isCoalescing = true
        }.newDocumentBuilder().parse(ByteArrayInputStream(raw)).also { it.documentElement.normalize() }

    private fun pullParser(input: InputStream): XmlPullParser {
        val xml = Class.forName("android.util.Xml")
        runCatching {
            xml.getMethod("resolvePullParser", InputStream::class.java).invoke(null, input) as XmlPullParser
        }.getOrNull()?.let { return it }
        val parser = xml.getMethod("newBinaryPullParser").invoke(null) as XmlPullParser
        parser.setInput(input, "UTF-8")
        return parser
    }

    private fun parsePull(raw: ByteArray): Document {
        val parser = pullParser(ByteArrayInputStream(raw))
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val stack = ArrayDeque<Element>()
        var event = parser.eventType
        while (true) {
            when (event) {
                XmlPullParser.START_DOCUMENT -> Unit
                XmlPullParser.START_TAG -> {
                    val element = doc.createElement(parser.name ?: throw fail("null tag name"))
                    for (i in 0 until parser.attributeCount) {
                        element.setAttribute(parser.getAttributeName(i), parser.getAttributeValue(i) ?: "")
                    }
                    if (stack.isEmpty()) doc.appendChild(element) else stack.last().appendChild(element)
                    stack.addLast(element)
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.orEmpty()
                    if (text.isNotBlank()) throw fail("unexpected text: ${text.take(60)}")
                }
                XmlPullParser.END_TAG -> {
                    val open = stack.removeLastOrNull() ?: throw fail("stray </${parser.name}>")
                    if (open.tagName != parser.name) {
                        throw fail("unbalanced tags: <${open.tagName}> vs </${parser.name}>")
                    }
                }
                XmlPullParser.END_DOCUMENT -> break
                else -> throw fail("unexpected token $event")
            }
            event = parser.next()
        }
        if (stack.isNotEmpty()) throw fail("unclosed tags remain")
        val root = doc.documentElement ?: throw fail("no root element")
        root.normalize()
        return doc
    }

    fun structuralCheck(doc: Document, log: (String) -> Unit) {
        val root = doc.documentElement?.tagName ?: throw fail("no root element")
        if (root != "packages") throw fail("unexpected root <$root>")
        val packages = doc.getElementsByTagName("package").length
        val sharedUsers = sharedUserElements(doc).map { it.getAttribute("name") }
        log("[*] packages=$packages shared-users=$sharedUsers")
        if (packages < 20) throw fail("suspiciously few <package> ($packages); refusing to touch")
        if (TARGET_SHARED_USER !in sharedUsers) {
            throw fail("shared-user $TARGET_SHARED_USER absent (must exist already; not creating it)")
        }
    }

    /** Decimal guard: writing INT_HEX/LONG_HEX values back as text would be silently corrupted by PMS. */
    fun guardIntAttrs(doc: Document) {
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val element = all.item(i) as? Element ?: continue
            val attrs = element.attributes ?: continue
            for (j in 0 until attrs.length) {
                val attr = attrs.item(j)
                if (attr.nodeName in INT_ATTRS && !attr.nodeValue.matches(Regex("-?\\d+"))) {
                    throw fail("${element.tagName}@${attr.nodeName}='${attr.nodeValue}' not decimal; refusing")
                }
            }
        }
    }

    fun certTable(doc: Document): MutableList<String?> {
        val table = mutableListOf<String?>()
        val certs = doc.getElementsByTagName("cert")
        for (i in 0 until certs.length) {
            val cert = certs.item(i) as? Element ?: continue
            val index = cert.getAttribute("index").toIntOrNull() ?: continue
            val key = cert.getAttribute("key").lowercase()
            if (key.isEmpty() || !HEX_RE.matches(key)) continue
            while (table.size < index) table.add(null)
            table.add(key)
        }
        return table
    }

    fun certKey(cert: Element, table: List<String?>): String? {
        val inline = cert.getAttribute("key").lowercase()
        if (inline.isNotEmpty()) return inline.takeIf(HEX_RE::matches)
        val index = cert.getAttribute("index").toIntOrNull() ?: return null
        return table.getOrNull(index)
    }

    /** Inject (DOM only, no disk): merge two certs for [keyHex] into the single pastSigs block;
     *  refuses when the key already exists (run --uninstall first). */
    fun inject(doc: Document, keyHex: String, log: (String) -> Unit): ByteArray {
        val key = normalizeKey(keyHex)
        if (!isKeyHex(key)) throw fail("key is not plausible cert hex (len=${key.length})")
        structuralCheck(doc, log)
        guardIntAttrs(doc)
        if (isInjected(doc, key)) throw fail("key already injected (run --uninstall first)")

        val target = targetSharedUser(doc) ?: throw fail("shared-user $TARGET_SHARED_USER absent")
        val table = certTable(doc)

        var maxIndex = -1
        var reuse: String? = null
        val certs = doc.getElementsByTagName("cert")
        for (i in 0 until certs.length) {
            val cert = certs.item(i) as? Element ?: continue
            cert.getAttribute("index").toIntOrNull()?.let { maxIndex = maxOf(maxIndex, it) }
            if (reuse == null && cert.hasAttribute("index") && cert.getAttribute("key").lowercase() == key) {
                reuse = cert.getAttribute("index")
            }
        }
        val freshIndex = reuse ?: maxOf(maxIndex + 1, table.size).toString()
        log(if (reuse != null) "[*] reuse existing cert index=$reuse" else "[*] fresh cert index=$freshIndex")

        var sigs = child(target, "sigs")
        if (sigs == null) {
            sigs = doc.createElement("sigs").also {
                it.setAttribute("count", "1")
                target.appendChild(it)
            }
        }

        val past = doc.createElement("pastSigs")
        var kept = 0
        children(sigs, "pastSigs").toList().forEach { old ->
            children(old, "cert").forEach { cert ->
                past.appendChild(cert)
                kept++
            }
            sigs.removeChild(old)
        }

        repeat(2) {
            val cert = doc.createElement("cert")
            cert.setAttribute("index", freshIndex)
            cert.setAttribute("key", key)
            cert.setAttribute("flags", FLAG_SHARED_USER)
            past.appendChild(cert)
        }
        past.setAttribute("count", (kept + 2).toString())
        sigs.appendChild(past)
        log("[+] pastSigs updated: kept $kept foreign cert(s) + 2 ours (index=$freshIndex)")
        return toXmlBytes(doc)
    }

    fun remove(doc: Document, keyHex: String, log: (String) -> Unit): Boolean {
        val key = normalizeKey(keyHex)
        if (!isKeyHex(key)) throw fail("key is not plausible cert hex")
        structuralCheck(doc, log)
        guardIntAttrs(doc)
        val target = targetSharedUser(doc) ?: throw fail("shared-user $TARGET_SHARED_USER absent")
        val sigs = child(target, "sigs") ?: return false
        val table = certTable(doc)
        var removed = false
        children(sigs, "pastSigs").toList().forEach { past ->
            val certs = children(past, "cert")
            val ours = certs.filter { certKey(it, table) == key }
            if (ours.isEmpty()) return@forEach
            if (ours.size == certs.size) {
                sigs.removeChild(past)
            } else {
                ours.forEach(past::removeChild)
                past.setAttribute("count", children(past, "cert").size.toString())
            }
            removed = true
        }
        if (removed) log("[+] removed our cert(s) from $TARGET_SHARED_USER pastSigs")
        return removed
    }

    fun isInjected(doc: Document, keyHex: String): Boolean {
        val key = normalizeKey(keyHex)
        val target = targetSharedUser(doc) ?: return false
        val sigs = child(target, "sigs") ?: return false
        val table = certTable(doc)
        return children(sigs, "pastSigs").any { past ->
            children(past, "cert").any { certKey(it, table) == key }
        }
    }

    fun verifyInjected(bytes: ByteArray, keyHex: String): String {
        val key = normalizeKey(keyHex)
        val doc = parse(bytes)
        val table = certTable(doc)
        val target = targetSharedUser(doc) ?: throw fail("shared-user missing after patch")
        val sigs = child(target, "sigs") ?: throw fail("sigs missing after patch")
        var ours = 0
        var total = 0
        children(sigs, "pastSigs").forEach { past ->
            children(past, "cert").forEach { cert ->
                total++
                if (certKey(cert, table) == key && cert.getAttribute("flags") == FLAG_SHARED_USER) ours++
            }
        }
        val consistent = children(sigs, "pastSigs").all {
            it.getAttribute("count") == children(it, "cert").size.toString()
        }
        if (ours < 2 || !consistent) {
            throw fail("verify failed: ours=$ours of $total, consistent=$consistent")
        }
        return "[verify] $TARGET_SHARED_USER pastSigs: ours=$ours of $total (flags=$FLAG_SHARED_USER), consistent=true"
    }

    fun verifyAbsent(bytes: ByteArray, keyHex: String): String {
        if (isInjected(parse(bytes), keyHex)) throw fail("verify failed: key still present")
        return "[verify] $TARGET_SHARED_USER our key absent=true"
    }

    fun summarize(raw: ByteArray): String {
        val text = StringBuilder()
        text.appendLine("[*] size=${raw.size} format=${if (isAbx(raw)) "ABX" else "TEXT"}")
        val doc = parse(raw)
        text.appendLine("[*] root=<${doc.documentElement?.tagName}> packages=${doc.getElementsByTagName("package").length}")
        text.appendLine(
            "[*] shared-users=" + sharedUserElements(doc).joinToString {
                "${it.getAttribute("name")}/${it.getAttribute("userId")}"
            },
        )
        return text.toString()
    }

    fun toXmlBytes(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8")
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun sharedUserElements(doc: Document): List<Element> =
        elements(doc.getElementsByTagName("shared-user"))

    private fun targetSharedUser(doc: Document): Element? =
        sharedUserElements(doc).firstOrNull { it.getAttribute("name") == TARGET_SHARED_USER }

    private fun elements(nodes: org.w3c.dom.NodeList): List<Element> =
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }

    private fun child(parent: Element, name: String): Element? =
        elements(parent.childNodes).firstOrNull { it.tagName == name }

    private fun children(parent: Element, name: String): List<Element> =
        elements(parent.childNodes).filter { it.tagName == name }

    private fun fail(message: String) = IllegalStateException("packages.xml: $message")
}
