package com.zhijian.reading.data

import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Splits a collection EPUB — the 作品集 shape: one file, dozens of books —
 * into one small EPUB per book.
 *
 * The collection's own table of contents (the NCX) is the map: every
 * top-level entry with a run of nested chapters behind it is one book. Each
 * split keeps the original spine documents, styles and images under their
 * original paths, so internal links and anchors keep working, and gets a
 * fresh OPF + NCX of its own. The new package sits at the zip root, so every
 * href is simply the file's own zip path.
 */
object EpubSplit {

    /** Fewer groups than this and the file is just a book, not a collection. */
    private const val MIN_BOOKS = 3

    /** A top-level entry with fewer documents is front matter (版权信息…). */
    private const val MIN_DOCS = 2

    /** A single book in the split: title, the written EPUB, its own cover. */
    data class Part(val title: String, val file: File, val cover: ByteArray?)

    private data class Item(val id: String, val href: String, val type: String)

    private data class Node(
        var label: String,
        var src: String,
        val children: MutableList<Node> = mutableListOf(),
    )

    /** Returns one [Part] per book, or null when [epub] is not a collection. */
    fun trySplit(epub: File, outDir: File, meta: FileMetadata?): List<Part>? = runCatching {
        ZipFile(epub).use { zip ->
            // Zip names and manifest hrefs may or may not be URL-encoded;
            // both spellings resolve to the same entry.
            val entries = HashMap<String, ZipEntry>()
            zip.entries().asSequence().forEach { e ->
                if (!e.isDirectory) {
                    entries[e.name] = e
                    runCatching { URLDecoder.decode(e.name, "UTF-8") }.getOrNull()
                        ?.let { entries.putIfAbsent(it, e) }
                }
            }

            val container = zip.entryText(entries["META-INF/container.xml"])
                ?: return@runCatching null
            val opfPath = Regex("""full-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(container)?.groupValues?.get(1) ?: return@runCatching null
            val opf = zip.entryText(entries[norm(opfPath)]) ?: return@runCatching null
            val opfDir = opfPath.substringBeforeLast('/', "")

            val manifest = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf)
                .mapNotNull { tag ->
                    val id = attr(tag.value, "id") ?: return@mapNotNull null
                    val href = attr(tag.value, "href") ?: return@mapNotNull null
                    Item(id, href, attr(tag.value, "media-type").orEmpty())
                }
                .associateBy { it.id }
            if (manifest.isEmpty()) return@runCatching null
            val spine = Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf)
                .mapNotNull { attr(it.value, "idref") }
                .toList()
            if (spine.isEmpty()) return@runCatching null

            // Reading-order position of every spine document, by zip path.
            val posOf = HashMap<String, Int>()
            spine.forEachIndexed { i, idref ->
                manifest[idref]?.let { posOf[norm(join(opfDir, it.href))] = i }
            }

            // The NCX is the collection's map: top-level nodes = books.
            val ncxItem = manifest.values.firstOrNull {
                it.type.equals("application/x-dtbncx+xml", true)
            } ?: return@runCatching null
            val ncxPath = norm(join(opfDir, ncxItem.href))
            val ncx = zip.entryText(entries[ncxPath]) ?: return@runCatching null
            val ncxDir = ncxPath.substringBeforeLast('/', "")
            val roots = parseNcx(ncx)
            if (roots.size < MIN_BOOKS) return@runCatching null

            data class Group(val title: String, val points: List<Pair<String, Int>>)

            // One group per top-level entry: every document its subtree
            // points at, in reading order.
            val groups = roots.map { root ->
                val pts = mutableListOf<Pair<String, Int>>()
                fun walk(n: Node) {
                    posOf[norm(join(ncxDir, n.src))]?.let { pts += n.label to it }
                    n.children.forEach { walk(it) }
                }
                walk(root)
                Group(cleanTitle(root.label), pts.distinctBy { it.second }.sortedBy { it.second })
            }.filter { it.points.size >= MIN_DOCS }
            if (groups.size < MIN_BOOKS) return@runCatching null

            // The groups must tile the spine in order. Documents between one
            // group's end and the next group's start belong to the former —
            // a chapter or postscript the NCX forgot to list.
            var last = -1
            for (g in groups) {
                if (g.points.first().second <= last) return@runCatching null
                last = g.points.last().second
            }
            val covered = HashSet<Int>()
            groups.forEach { g -> g.points.forEach { covered += it.second } }
            val patched = groups.map { g ->
                var end = g.points.last().second
                var extra = 0
                while (end + 1 + extra < spine.size && (end + 1 + extra) !in covered) extra++
                if (extra > 0) {
                    Group(g.title, g.points + List(extra) { "" to (end + 1 + it) })
                } else g
            }

            val author = meta?.authors?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "Unknown"
            outDir.mkdirs()
            val parts = mutableListOf<Part>()
            patched.forEachIndexed { gi, g ->
                val out = File(outDir, "part-$gi.epub")
                val cover = firstImage(zip, entries, manifest, spine, g.points.take(4).map { it.second })
                if (writePart(zip, entries, opfDir, manifest, spine, g.title, author, g.points, cover, out)) {
                    parts += Part(g.title, out, cover)
                }
            }
            if (parts.size < MIN_BOOKS) {
                parts.forEach { it.file.delete() }
                return@runCatching null
            }
            parts
        }
    }.getOrNull()

    // ---- one book out of the collection -------------------------------------

    private fun writePart(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        opfDir: String,
        manifest: Map<String, Item>,
        spine: List<String>,
        title: String,
        author: String,
        points: List<Pair<String, Int>>,
        cover: ByteArray?,
        out: File,
    ): Boolean {
        val docs = points.map { it.second }
        // This book's documents, plus everything they reference.
        val used = LinkedHashMap<String, Item>() // zip path -> manifest item
        val docPaths = docs.map { i ->
            val item = manifest[spine.getOrNull(i)] ?: return false
            val path = norm(join(opfDir, item.href))
            used[path] = item
            path
        }
        val seen = HashSet(docPaths)
        docPaths.forEach { pullAssets(zip, entries, it, used, seen) }
        // Every document must exist in the source, or the part would be a
        // shell with nothing to read.
        if (used.size < docPaths.size || docPaths.any { !entries.containsKey(it) }) return false

        runCatching {
            ZipOutputStream(out.outputStream().buffered()).use { z ->
                val mime = "application/epub+zip".toByteArray()
                z.putNextEntry(ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mime.size.toLong(); compressedSize = mime.size.toLong()
                    crc = CRC32().apply { update(mime); }.value
                })
                z.write(mime); z.closeEntry()

                z.text(
                    "META-INF/container.xml",
                    """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""",
                )

                // Files keep their original zip paths; the package sits at
                // the root, so an href is the file's own path.
                used.forEach { (path, _) ->
                    val src = entries[path] ?: return@runCatching
                    z.putNextEntry(ZipEntry(path))
                    zip.getInputStream(src).use { input -> input.copyTo(z) }
                    z.closeEntry()
                }
                val manifestSb = StringBuilder()
                used.forEach { (path, item) ->
                    manifestSb.append(
                        """    <item id="${esc(item.id)}" href="${esc(path)}" media-type="${esc(item.type)}"/>""",
                    ).append('\n')
                }
                val coverMeta = if (cover != null) {
                    z.putNextEntry(ZipEntry("cover.jpg")); z.write(cover); z.closeEntry()
                    manifestSb.append(
                        """    <item id="pl-cover" href="cover.jpg" media-type="image/jpeg"/>""",
                    ).append('\n')
                    """    <meta name="cover" content="pl-cover"/>"""
                } else ""
                manifestSb.append(
                    """    <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""",
                ).append('\n')

                val spineSb = StringBuilder()
                docs.forEach { i ->
                    spineSb.append("""    <itemref idref="${esc(spine[i])}"/>""").append('\n')
                }

                val metaSb = buildString {
                    append("""    <dc:title>${esc(title)}</dc:title>""").append('\n')
                    append("""    <dc:creator opf:role="aut">${esc(author)}</dc:creator>""").append('\n')
                    append("""    <dc:language>zh</dc:language>""").append('\n')
                    append("""    <dc:identifier id="bookid">urn:uuid:${UUID.randomUUID()}</dc:identifier>""")
                    append('\n')
                    append(coverMeta)
                }

                z.text(
                    "content.opf",
                    """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
$metaSb  </metadata>
  <manifest>
$manifestSb  </manifest>
  <spine toc="toc">
$spineSb  </spine>
</package>""",
                )

                // The chapters, in reading order, labels kept from the map.
                z.text(
                    "toc.ncx",
                    buildString {
                        append(
                            """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="urn:uuid:${UUID.randomUUID()}"/></head>
  <docTitle><text>${esc(title)}</text></docTitle>
  <navMap>""",
                        )
                        points.forEachIndexed { i, (label, pos) ->
                            val item = manifest[spine.getOrNull(pos)] ?: return@forEachIndexed
                            val href = norm(join(opfDir, item.href))
                            append(
                                """
  <navPoint id="np$i" playOrder="${i + 1}">
    <navLabel><text>${esc(label.ifBlank { "第 ${i + 1} 节" })}</text></navLabel>
    <content src="${esc(href)}"/>
  </navPoint>""",
                            )
                        }
                        append("\n  </navMap>\n</ncx>")
                    },
                )
            }
        }.onFailure { out.delete(); return false }
        // A split book is only landed when it reads back as a book with its
        // chapters actually inside.
        val reads = EpubMeta.parse(out) != null
        val complete = runCatching {
            ZipFile(out).use { check -> docPaths.all { check.getEntry(it) != null } }
        }.getOrDefault(false)
        if (!reads || !complete) out.delete()
        return reads && complete
    }

    /** Copies into [used] every asset the document at [docPath] references. */
    private fun pullAssets(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        docPath: String,
        used: LinkedHashMap<String, Item>,
        seen: MutableSet<String>,
    ) {
        val dir = docPath.substringBeforeLast('/', "")
        val text = zip.entryText(entries[docPath]) ?: return
        Regex("""(?:src|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.groupValues[1] }
            .forEach { raw ->
                if (raw.startsWith("#") || raw.contains(':')) return@forEach
                val path = norm(join(dir, raw))
                if (!seen.add(path)) return@forEach
                val entry = entries[path] ?: return@forEach
                val media = mediaType(path) ?: return@forEach
                used[path] = Item("pl-a${used.size}", path, media)
                if (media == "text/css") {
                    // A stylesheet pulls its own images in.
                    Regex("""url\(\s*['"]?([^)'"]+)['"]?\s*\)""", RegexOption.IGNORE_CASE)
                        .findAll(zip.entryText(entry) ?: "")
                        .map { it.groupValues[1] }
                        .forEach { u ->
                            if (u.contains(':') || u.startsWith("#")) return@forEach
                            val p = norm(join(dir, u))
                            if (!seen.add(p)) return@forEach
                            val m = mediaType(p) ?: return@forEach
                            if (entries.containsKey(p)) used[p] = Item("pl-a${used.size}", p, m)
                        }
                }
            }
    }

    private fun mediaType(path: String): String? = when {
        path.endsWith(".png", true) -> "image/png"
        path.endsWith(".gif", true) -> "image/gif"
        path.endsWith(".svg", true) -> "image/svg+xml"
        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
        path.endsWith(".css", true) -> "text/css"
        path.endsWith(".ttf", true) || path.endsWith(".otf", true) -> "font/ttf"
        path.endsWith(".woff", true) -> "font/woff"
        path.endsWith(".woff2", true) -> "font/woff2"
        path.endsWith(".ncx", true) -> null
        path.endsWith(".xhtml", true) || path.endsWith(".html", true) ||
            path.endsWith(".htm", true) || path.endsWith(".opf", true) -> null
        else -> null
    }

    /** The book's own cover: a `cover`-named image among its opening
     *  documents; otherwise the first picture they carry. */
    private fun firstImage(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        manifest: Map<String, Item>,
        spine: List<String>,
        positions: List<Int>,
    ): ByteArray? {
        val docPaths = positions.mapNotNull { pos ->
            manifest[spine.getOrNull(pos)]?.let { norm(join("", it.href)) }
        }
        val images = mutableListOf<Pair<String, String>>() // path to src
        for (path in docPaths) {
            val text = zip.entryText(entries[path]) ?: continue
            Regex("""<img\b[^>]*src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .map { it.groupValues[1] }
                .filter { !it.startsWith("http") && !it.startsWith("data:") && !it.startsWith("#") }
                .forEach { src ->
                    val imgPath = norm(join(path.substringBeforeLast('/', ""), src))
                    if (entries.containsKey(imgPath)) images += imgPath to src
                }
        }
        val wanted = images.firstOrNull { it.second.contains("cover", true) }
        for (pick in listOfNotNull(wanted, images.firstOrNull())) {
            val bytes = entries[pick.first]?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
            if (bytes != null && bytes.size > 512) return bytes
        }
        return null
    }

    // ---- parsing and small tools ---------------------------------------------

    /** NCX → ordered tree of top-level nodes (label + first content src). */
    private fun parseNcx(ncx: String): List<Node> {
        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        val ev = Regex(
            """<navPoint\b|</navPoint>|<text>([\s\S]*?)</text>|<content\b[^>]*src\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        for (m in ev.findAll(ncx)) {
            when {
                m.value.startsWith("<navPoint") -> stack.addLast(Node("", ""))
                m.value == "</navPoint>" -> {
                    val done = stack.removeLastOrNull() ?: continue
                    val parent = stack.lastOrNull()
                    if (parent == null) roots += done else parent.children += done
                }
                m.groupValues[1].isNotEmpty() ->
                    stack.lastOrNull()?.let { if (it.label.isEmpty()) it.label = xmlText(m.groupValues[1]) }
                m.groupValues[2].isNotEmpty() ->
                    stack.lastOrNull()?.let { if (it.src.isEmpty()) it.src = m.groupValues[2] }
            }
        }
        return roots
    }

    private fun xmlText(s: String) = s.trim()
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")

    /** `dir` + relative `href`, resolved against `.`/`..`, fragment dropped. */
    private fun join(dir: String, href: String): String {
        val path = href.substringBefore('#').trim()
        if (path.isEmpty()) return ""
        val parts = ArrayDeque<String>()
        if (dir.isNotEmpty()) dir.split('/').forEach { parts.addLast(it) }
        path.split('/').forEach { seg ->
            when (seg) {
                ".", "" -> {}
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    /** Zip entry name for [path]: URL-decoded when it is spelled encoded. */
    private fun norm(path: String): String = runCatching {
        URLDecoder.decode(path, "UTF-8")
    }.getOrDefault(path)

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)

    private fun ZipFile.entryText(entry: ZipEntry?): String? =
        entry?.let { getInputStream(it).use { input -> input.readBytes().toString(Charsets.UTF_8) } }

    /** Titles arrive as `嫌疑人X的献身（2005）` — kept, minus stray gaps. */
    private fun cleanTitle(s: String) = s.trim().replace(Regex("\\s+"), " ")

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun ZipOutputStream.text(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
