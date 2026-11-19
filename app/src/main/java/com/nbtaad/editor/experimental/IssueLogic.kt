package com.nbtaad.editor.experimental

import com.nbtaad.editor.install.Names

/** One issue or pull request of the project, as GitHub lists them (both come from the same list). */
data class Topic(
    val number: Int,
    val isPr: Boolean,
    val title: String,
    val author: String,
    val open: Boolean,
    val merged: Boolean,
    val draft: Boolean,
    val comments: Int,
    val created: String,
    val updated: String,
    val labels: List<String>,
    val body: String,
    val url: String,
)

/** One comment on an issue or pull request. */
data class Comment(val topic: Int, val author: String, val created: String, val body: String, val url: String)

/** What a link in a text points at. */
enum class FileKind {
    /** A file the installer can look into: a zip, or one of a build's own files. */
    BUILD,
    /** Some other file (a log, a text, an apk). It can be opened in the browser. */
    OTHER,
    /** GitHub shows these only to someone who is signed in (a build artifact of an Actions run). */
    NEEDS_LOGIN,
}

/** A link to a file in a text, where [by] wrote it and [where] it stands ("the first post", "a comment"). */
data class FileLink(val url: String, val name: String, val kind: FileKind, val by: String, val where: String) {
    val installable: Boolean get() = kind == FileKind.BUILD
}

/** A short digest made on the phone from the words of a page, and the links in it. Not an AI summary. */
data class Summary(val lines: List<String>, val links: List<String>)

/** Reading GitHub's answers. */
object IssueParser {
    fun topics(json: String): List<Topic> {
        val arr = Json.parse(json).asList() ?: return emptyList()
        return arr.mapNotNull { it.asObj()?.let(::topic) }
    }

    private fun topic(o: Map<String, Any?>): Topic? {
        val n = o.int("number")
        if (n <= 0) return null
        val pr = o.asObjOrNull("pull_request")
        return Topic(
            number = n,
            isPr = pr != null,
            title = o.str("title").trim(),
            author = o.asObjOrNull("user")?.str("login") ?: "",
            open = o.str("state") == "open",
            merged = pr != null && pr["merged_at"] != null,
            draft = o.bool("draft"),
            comments = o.int("comments"),
            created = o.str("created_at"),
            updated = o.str("updated_at"),
            labels = (o["labels"].asList() ?: emptyList()).mapNotNull { l ->
                l.asObj()?.str("name")?.takeIf { it.isNotBlank() } ?: (l as? String)
            },
            body = o.str("body"),
            url = o.str("html_url"),
        )
    }

    fun comments(json: String): List<Comment> {
        val arr = Json.parse(json).asList() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.asObj() ?: return@mapNotNull null
            Comment(
                topic = numberFromUrl(o.str("issue_url")) ?: numberFromUrl(o.str("html_url")) ?: 0,
                author = o.asObjOrNull("user")?.str("login") ?: "",
                created = o.str("created_at"),
                body = o.str("body"),
                url = o.str("html_url"),
            )
        }
    }

    /** ".../issues/45" or ".../issues/45#issuecomment-1" gives 45. */
    fun numberFromUrl(url: String): Int? =
        Regex("""/(?:issues|pull)/(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()

    private fun Map<String, Any?>.asObjOrNull(k: String): Map<String, Any?>? = this[k].asObj()
}

/** Links and files in the free text of a page. */
object Links {
    private val URL = Regex("""https?://[^\s<>"'`\[\]()]+""")
    private val ARCHIVE = listOf(".zip", ".nbtpkg", ".7z", ".tar", ".gz", ".tgz", ".rar", ".apk")
    private val PICTURE = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".bmp", ".mp4", ".mov", ".webm", ".heic")

    /** Every link in [text] (also the ones in markdown), once each, in the order they appear. */
    fun find(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in URL.findAll(text)) out += m.value.trimEnd('.', ',', ';', ':', '!', '?', '*', '_', '>')
        return out.toList()
    }

    private fun path(url: String): String =
        url.substringAfter("://").substringAfter('/', "").substringBefore('#').substringBefore('?')

    private fun host(url: String): String = url.substringAfter("://").substringBefore('/').lowercase()

    private fun decode(s: String): String = try {
        java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        s
    }

    /** True for a picture or video: not what this is looking for. */
    fun isPicture(url: String): Boolean {
        val h = host(url)
        val p = path(url).lowercase()
        return h.contains("user-images.githubusercontent.com") || h.contains("private-user-images") ||
            p.startsWith("user-attachments/assets/") || PICTURE.any { p.endsWith(it) }
    }

    /** The file [url] points at, or null when it is a page, a picture or something else. */
    fun file(url: String, by: String, where: String): FileLink? {
        if (!url.startsWith("https://")) return null
        if (isPicture(url)) return null
        val h = host(url)
        val p = path(url)
        val name = decode(p.substringAfterLast('/')).ifBlank { return null }
        val lower = name.lowercase()
        val isFileUrl = (h == "github.com" || h == "www.github.com") && (
            p.startsWith("user-attachments/files/") ||
                Regex("""^[^/]+/[^/]+/files/\d+/""").containsMatchIn(p) ||
                Regex("""^[^/]+/[^/]+/releases/download/""").containsMatchIn(p) ||
                Regex("""^[^/]+/[^/]+/raw/""").containsMatchIn(p)
            ) || h == "raw.githubusercontent.com" || h.endsWith("objects.githubusercontent.com")
        if (h == "github.com" && Regex("""^[^/]+/[^/]+/actions/runs/\d+/artifacts/\d+""").containsMatchIn(p)) {
            return FileLink(url, "build artifact of an Actions run", FileKind.NEEDS_LOGIN, by, where)
        }
        val archive = ARCHIVE.any { lower.endsWith(it) }
        if (!isFileUrl && !archive) return null
        val build = lower.endsWith(".zip") || lower.endsWith(".nbtpkg") || Names.canonical(name) != null || Names.isContainer(name)
        return FileLink(url, name, if (build) FileKind.BUILD else FileKind.OTHER, by, where)
    }

    /** The files linked in a text. */
    fun files(text: String, by: String, where: String): List<FileLink> =
        find(text).mapNotNull { file(it, by, where) }.distinctBy { it.url }

    /** True when what came back is a web page (a sign-in page, say) and not the file that was asked for. */
    fun looksLikePage(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1).trimStart().lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    /** Links for a reader to follow: not pictures, not files (those are listed on their own). */
    fun pages(text: String): List<String> = find(text).filter { !isPicture(it) && file(it, "", "") == null }
}

/** Turning a page's words into something that reads well in a small list. */
object PageText {
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val IMAGE_MD = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val TAG = Regex("""</?[a-zA-Z][^>]*>""")
    private val LINK_MD = Regex("""\[([^\]]+)]\((https?://[^)\s]+)[^)]*\)""")
    private val MANY_BLANK = Regex("""\n{3,}""")

    /** Markdown made readable: pictures and HTML go, "[text](url)" becomes "text". */
    fun clean(raw: String): String {
        var t = raw.replace("\r\n", "\n").replace('\r', '\n')
        t = COMMENT.replace(t, "")
        t = IMAGE_MD.replace(t, "[picture]")
        t = LINK_MD.replace(t) { it.groupValues[1] }
        t = TAG.replace(t, "")
        t = t.lines().joinToString("\n") { it.trimEnd() }
        return MANY_BLANK.replace(t, "\n\n").trim()
    }

    /** The first sentences of a text that say something: no headings, code or lists of links. Cut at [max] characters. */
    fun gist(raw: String, sentences: Int = 2, max: Int = 260): String {
        val t = clean(raw)
        val noCode = t.split("```").filterIndexed { i, _ -> i % 2 == 0 }.joinToString("\n")
        val lines = noCode.lines().map { it.trim().trimStart('#', '>', '-', '*', ' ') }
            .filter { it.length > 3 && !it.startsWith("http") && !it.endsWith(":") || it.length > 40 }
        val flat = lines.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
        if (flat.isEmpty()) return ""
        val parts = Regex("""(?<=[.!?])\s+""").split(flat)
        val picked = parts.take(sentences).joinToString(" ")
        return if (picked.length > max) picked.take(max).trimEnd() + "…" else picked
    }
}

/** What a page mentions that the app can offer: settings named like NBT_SOMETHING. */
object Mentions {
    private val KEY = Regex("""\bNBT_[A-Z0-9_]{2,}\b""")

    /** Setting names in the texts, the ones named most often first. A trailing "_" (as in "NBT_*") is not a name. */
    fun settings(texts: List<String>): List<String> {
        val count = LinkedHashMap<String, Int>()
        for (t in texts) for (m in KEY.findAll(t)) {
            val k = m.value
            if (k.endsWith("_")) continue
            count[k] = (count[k] ?: 0) + 1
        }
        return count.entries.sortedByDescending { it.value }.map { it.key }
    }
}

/** The digest of a page. */
object Digest {
    fun of(t: Topic, comments: List<Comment>): Summary {
        val lines = ArrayList<String>()
        val kind = if (t.isPr) "Pull request" else "Issue"
        val state = when {
            t.merged -> "merged"
            t.open -> if (t.draft) "open (draft)" else "open"
            else -> "closed"
        }
        val people = (listOf(t.author) + comments.map { it.author }).filter { it.isNotBlank() }.distinct()
        lines += "$kind #${t.number}, $state, opened by ${t.author.ifBlank { "someone" }} on ${t.created.take(10)}. " +
            "${comments.size} comment${if (comments.size == 1) "" else "s"} from ${people.size} " +
            "${if (people.size == 1) "person" else "people"}, last activity ${t.updated.take(10)}."
        if (t.labels.isNotEmpty()) lines += "Labels: ${t.labels.joinToString(", ")}."
        val gist = PageText.gist(t.body)
        if (gist.isNotEmpty()) lines += "It says: $gist"
        val last = comments.lastOrNull { PageText.gist(it.body).isNotEmpty() }
        if (last != null) lines += "Latest from ${last.author} (${last.created.take(10)}): ${PageText.gist(last.body, 1, 220)}"
        val all = listOf(t.body) + comments.map { it.body }
        val settings = Mentions.settings(all)
        if (settings.isNotEmpty()) lines += "Settings mentioned: ${settings.take(8).joinToString(", ")}."
        val files = filesOf(t, comments)
        if (files.isNotEmpty()) lines += "Files shared: ${files.take(5).joinToString(", ") { it.name }}."
        val pages = all.flatMap { Links.pages(it) }.distinct().filter { it != t.url }.take(8)
        return Summary(lines, files.map { it.url }.take(8) + pages)
    }

    /** The files linked in the first post and the comments. */
    fun filesOf(t: Topic, comments: List<Comment>): List<FileLink> {
        val out = ArrayList<FileLink>()
        out += Links.files(t.body, t.author, "the first post")
        for (c in comments) out += Links.files(c.body, c.author, "a comment on ${c.created.take(10)}")
        return out.distinctBy { it.url }
    }
}

/** What GitHub says about how many requests are left. */
object RateLimit {
    /** "GitHub allows 60 requests an hour without signing in; they are used up. Try again at 14:35." */
    fun usedUp(resetEpochSec: Long?, now: Long = System.currentTimeMillis() / 1000, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val base = "GitHub allows 60 requests an hour without signing in, and they are used up."
        if (resetEpochSec == null || resetEpochSec <= 0) return "$base Try again in a while."
        val at = java.time.Instant.ofEpochSecond(resetEpochSec).atZone(zone)
        val mins = maxOf(1L, (resetEpochSec - now + 59) / 60)
        return "$base Try again at ${"%02d:%02d".format(at.hour, at.minute)} (in about $mins minute${if (mins == 1L) "" else "s"})."
    }
}
