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
                --ink: #26241f; --dim: #8b877c; --bg: #fbf9f4; --rule: #e7e3d9;
                --link: #3a5bc7; --selbg: #e1e9ff; --hlbg: rgba(255,196,0,.42); --codebg: #f3f1ea;
              }
              html.pl-dark {
                --ink: #b5b3ac; --dim: #7d7b74; --bg: #17181a; --rule: #2e2f31;
                --link: #8fa6ce; --selbg: #24304f; --hlbg: rgba(255,196,0,.30); --codebg: #1d1e20;
              }
              html.pl-sepia {
                --ink: #332c1e; --dim: #7a6e58; --bg: #f5eedc; --rule: #e3d9c3;
                --link: #8a5a2b; --selbg: #e8dcc2; --hlbg: rgba(240,170,20,.38); --codebg: #efe6d0;
              }
              html.pl-gray {
                --ink: #33322f; --dim: #6b6963; --bg: #e4e2dd; --rule: #d0ccc4;
                --link: #4a6584; --selbg: #d3d1cb; --hlbg: rgba(230,180,40,.40); --codebg: #dad7d1;
              }
              :root { color-scheme: light; }
              html.pl-dark { color-scheme: dark; }
              body {
                margin: 0; padding: 61px 30px 57px;
                background: var(--bg); color: var(--ink);
                font-family: -apple-system, 'Noto Sans', 'Microsoft YaHei', sans-serif;
                font-size: 20px; line-height: 1.9;
                -webkit-text-size-adjust: 100%;
                word-wrap: break-word;
              }
              section.chapter + section.chapter { margin-top: 2.4em; padding-top: 1.8em; border-top: 1px solid var(--rule); }
              /* Paged mode: every chapter opens on its own page, 微信读书
                 style — no trailing rule, no leftover of the previous
                 chapter at the top. The forced break is dropped by the
                 engine for the first section, so page 1 stays page 1. */
              html.pl-paged section.chapter { break-before: column; margin-top: 0; padding-top: 0; border-top: 0; }
              html.pl-paged section.chapter > h1:first-child,
              html.pl-paged section.chapter > h2:first-child,
              html.pl-paged section.chapter > h3:first-child { margin-top: .35em; }
              /* A heading stranded at a page bottom looks broken; keep it
                 with at least the first lines of its paragraph. */
              h1, h2, h3, h4 { break-inside: avoid; break-after: avoid-column; }
              h1, h2, h3, h4 { line-height: 1.32; margin: 1.6em 0 .8em; font-weight: 600; }
              h1 { font-size: 1.35em; } h2 { font-size: 1.18em; } h3 { font-size: 1.06em; }
              /* 微信读书 paragraphs are flush-left with paragraph spacing, no indent.
                 orphans/widows must be 1: Chromium's default of 2 pulls a whole
                 extra line off the page bottom whenever the break would leave a
                 single line, leaving a conspicuous band of air (measured 290px
                 on a real page) that WeChat Read never shows. */
              p { margin: 0 0 .9em; text-align: justify; orphans: 1; widows: 1; }
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
