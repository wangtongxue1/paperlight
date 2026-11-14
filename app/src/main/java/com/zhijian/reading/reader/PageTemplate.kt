package com.zhijian.reading.reader

/**
 * The one HTML shell every format renders into. Rules live here so a TXT,
 * an FB2 and an EPUB look the same on screen; the reader's JS engine
 * (assets/reader.js) drives paging, highlights and search on top of it.
 *
 * Every chapter is a `<section class="chapter">`, which is what the TOC,
 * the tap-to-next-chapter and the highlight anchoring all key on.
 */
object PageTemplate {

    fun wrap(title: String, body: String, theme: String): String {
        val cls = when (theme) {
            "dark" -> "pl-dark"
            "sepia" -> "pl-sepia"
            "gray" -> "pl-gray"
            else -> ""
        }
        return """
            <!DOCTYPE html>
            <html${if (cls.isNotEmpty()) " class=\"$cls\"" else ""}><head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
            <title>${escape(title)}</title>
            <style>
              :root {
                --ink: #21201c; --dim: #4a463f; --bg: #ffffff; --rule: #e2e1de;
                --link: #3a5bc7; --selbg: #e1e9ff; --hlbg: rgba(255,196,0,.42); --codebg: #f4f3f1;
              }
              html.pl-dark {
                --ink: #eeeeec; --dim: #b5b3ad; --bg: #111110; --rule: #31312e;
                --link: #9eb1ff; --selbg: #1d2e62; --hlbg: rgba(255,196,0,.30); --codebg: #191918;
              }
              html.pl-sepia {
                --ink: #3b3225; --dim: #6b5d49; --bg: #f7efe0; --rule: #e0d4bd;
                --link: #8a5a2b; --selbg: #e8dcc2; --hlbg: rgba(240,170,20,.38); --codebg: #efe5d2;
              }
              html.pl-gray {
                --ink: #3a3a3a; --dim: #666560; --bg: #e3e1dc; --rule: #cdc9c1;
                --link: #4a6584; --selbg: #d3d1cb; --hlbg: rgba(230,180,40,.40); --codebg: #d9d6d0;
              }
              :root { color-scheme: light; }
              html.pl-dark { color-scheme: dark; }
              body {
                margin: 0; padding: 44px 22px 64px;
                background: var(--bg); color: var(--ink);
                font-family: Georgia, 'Noto Serif', 'Songti SC', serif;
                font-size: 17px; line-height: 1.78;
                -webkit-text-size-adjust: 100%;
                word-wrap: break-word;
              }
              section.chapter + section.chapter { margin-top: 2.2em; padding-top: 1.6em; border-top: 1px solid var(--rule); }
              h1, h2, h3, h4 { font-family: -apple-system, 'Noto Sans', sans-serif; line-height: 1.32; margin: 1.6em 0 .8em; }
              h1 { font-size: 1.42em; } h2 { font-size: 1.22em; } h3 { font-size: 1.08em; }
              p { margin: 0 0 1em; text-indent: 1.5em; text-align: justify; }
              p:first-of-type, h1 + p, h2 + p, h3 + p { text-indent: 0; }
              img { max-width: 100%; height: auto; display: block; margin: 1.2em auto; }
              blockquote { margin: 1em 0; padding-left: 1em; border-left: 2px solid var(--rule); color: var(--dim); }
              a { color: var(--link); text-decoration: none; }
              pre { white-space: pre-wrap; font-size: .86em; background: var(--codebg); padding: 10px 12px; border-radius: 6px; overflow-x: auto; }
              hr { border: 0; border-top: 1px solid var(--rule); margin: 1.8em 0; }
              ::selection { background: var(--selbg); }
              span.pl-hl {
                background: var(--hlbg); color: inherit;
                -webkit-box-decoration-break: clone; box-decoration-break: clone;
                border-radius: 2px; padding: 1px 0;
              }
              span.pl-hl.pl-c-green { background: rgba(102,204,102,.42); }
              span.pl-hl.pl-c-blue { background: rgba(96,160,255,.32); }
              span.pl-hl.pl-c-pink { background: rgba(255,102,163,.38); }
              span.pl-hl.pl-c-underline {
                background: transparent; border-radius: 0; padding: 0;
                text-decoration: underline; text-decoration-thickness: 2px;
                text-underline-offset: 4px; text-decoration-color: currentColor;
              }
              span.pl-hit { background: rgba(255,196,0,.46); border-radius: 2px; }
              span.pl-hit-on { background: rgba(255,150,0,.72); }
              #pl-pager::-webkit-scrollbar { display: none; }
            </style>
            </head><body>$body</body></html>
        """.trimIndent()
    }

    /** Wraps one chapter's inner HTML in the section the engine expects. */
    fun chapter(index: Int, inner: String): String =
        "<section class=\"chapter\" id=\"ch$index\">$inner</section>"

    fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun stripTags(s: String): String = s.replace(Regex("<[^>]*>"), "")

    fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&hellip;", "…")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")

    /** A plain-text chunk → escaped HTML paragraphs. */
    fun paragraphs(text: String): String {
        val sb = StringBuilder()
        text.split('\n').forEach { raw ->
            val line = raw.trimEnd('\r').trim()
            if (line.isEmpty()) return@forEach
            sb.append("<p>").append(escape(line)).append("</p>")
        }
        return sb.toString()
    }
}
