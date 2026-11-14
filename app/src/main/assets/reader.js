/*
 * Paperlight reader engine.
 * Loaded into the rendered book page by the app after the document is ready.
 * Talks to Android through the AndroidReader JavascriptInterface:
 *   onEvent(type, json)  with types "tap", "metrics", "selection".
 * Android calls back on window.__pl (setMode, setFontScale, setTheme,
 * setLineHeight, setFontFamily, jumpChapter, jumpRatio, scrollScreens,
 * next, prev, getState, applyHighlight, setStyle, highlightByText,
 * removeHighlight, clearSelection).
 */
(function () {
  if (window.__pl) { return; }

  var R = window.__pl = {
    mode: 'scroll',      // 'scroll' | 'paged'
    theme: 'light',      // 'light' | 'sepia' | 'gray' | 'dark'
    ratio: 0,            // 0..1 position in the book
    page: 0, pages: 1, chapter: 0,
  };

  var body = document.body;
  var doc = document.documentElement;
  var pager = null, flow = null;
  // 微信读书 proportions: the text column floats with air above and below,
  // never flush with the screen edges — top ≈ 10% of the viewport for the
  // quiet chapter mark, bottom ≈ 11% so the page number never touches text.
  var PAD = 24;   // horizontal padding inside each column, matches the page's
  var GAP = 2 * PAD;  // gutter = two page margins, so each page shows
                      // [margin][column][margin] with symmetric 24px edges
  var PADTOP = 80, PADBOT = 92;
  var STEP = 0;   // distance from one column's start to the next

  function post(type, data) {
    try { window.AndroidReader.onEvent(type, JSON.stringify(data || {})); } catch (e) { }
  }


  function secs() { return document.querySelectorAll('section.chapter'); }

  // ---------- paged mode ----------

  function enablePaged() {
    if (!pager) {
      pager = document.createElement('div');
      pager.id = 'pl-pager';
      flow = document.createElement('div');
      flow.id = 'pl-flow';
      pager.style.cssText =
        'position:fixed;top:0;left:0;right:0;bottom:0;overflow-x:hidden;overflow-y:hidden;overflow-anchor:none;';
      while (body.firstChild) flow.appendChild(body.firstChild);
      pager.appendChild(flow);
      body.appendChild(pager);
      body.style.padding = '0';
      doc.style.overflow = 'hidden';
      window.scrollTo(0, 0);
    }
    layout();
  }

  function disablePaged() {
    if (!pager) return;
    while (flow.firstChild) body.appendChild(flow.firstChild);
    pager.remove();
    pager = null; flow = null;
    body.style.padding = '';
    doc.style.overflow = '';
    clampScroll();
  }

  // Scroll mode must never pan sideways: a wide child (long code line, an
  // unbroken URL) would otherwise let horizontal swipe drag the whole page.
  function clampScroll() {
    doc.style.overflowX = 'hidden';
    body.style.overflowX = 'hidden';
    body.style.touchAction = 'pan-y';
  }
  clampScroll();

  function layout() {
    if (!pager || !flow) return;
    var h = pager.clientHeight, w = pager.clientWidth;
    // clientWidth rounds to whole CSS pixels, but some screens give a
    // fractional viewport (e.g. 1220 physical px at dpr 3.25 = 375.3846).
    // Laying columns out against the rounded width makes every column drift
    // 0.38px off the scroll grid — invisible on page 1, half a page off by
    // page 480. Measure the exact width instead so the grid matches the
    // renderer's own fractional column pitch.
    var exact = pager.getBoundingClientRect().width;
    if (exact > 0) w = exact;
    // The column box lives inside the flow's own horizontal padding, so the
    // usable column width is the viewport minus both pads. Making the column
    // exactly that wide puts one column per screen and keeps every page
    // offset aligned with the scroll math.
    var col = Math.max(100, w - 2 * PAD);
    STEP = col + GAP;
    // height is content-box: the column box must be the viewport MINUS the
    // top/bottom padding, or every page bleeds past the reserved air and
    // text runs flush to the screen edge (the "full-bleed" bug).
    flow.style.height = (h - PADTOP - PADBOT) + 'px';
    flow.style.columnWidth = col + 'px';
    flow.style.columnGap = GAP + 'px';
    flow.style.columnFill = 'auto';
    flow.style.position = 'relative';
    flow.style.padding = PADTOP + 'px ' + PAD + 'px ' + PADBOT + 'px';
    // Trailing padding is not part of the scrollable overflow, so the last
    // column could never land on its right margin. Pin an invisible 1px
    // strut just past it so the final page offset becomes reachable.
    var strut = document.getElementById('pl-endstrut');
    if (!strut) {
      strut = document.createElement('div');
      strut.id = 'pl-endstrut';
      strut.style.cssText = 'position:absolute;top:0;height:1px;';
      flow.appendChild(strut);
    }
    strut.style.display = 'none';
    strut.style.left = flow.scrollWidth + 'px';
    strut.style.width = PAD + 'px';
    strut.style.display = 'block';
  }

  function pageCount() {
    if (!pager || !flow || STEP <= 0) return 1;
    return Math.max(1, Math.round((flow.scrollWidth - 2 * PAD + GAP) / STEP));
  }

  /** Scroll offset for page p, never past the flow's right padding edge. */
  function colScroll(p) {
    if (!pager || !flow || STEP <= 0) return 0;
    var max = Math.max(0, flow.scrollWidth - pager.clientWidth);
    return Math.max(0, Math.min(p * STEP, max));
  }

  function gotoPage(p, smooth) {
    if (!pager) return;
    var n = pageCount();
    p = Math.max(0, Math.min(n - 1, p));
    if (smooth) {
      // Glide with a transform instead of a native smooth scroll: native
      // smooth scrolling can be cancelled halfway by the next touch and
      // leave the pager parked between two columns.
      commitDrag(p, R.page);
    } else {
      pager.scrollLeft = colScroll(p);
      animUntil = Date.now() + 300;
      setTimeout(postMetrics, 80);
    }
  }

  // ---------- landing snap ----------

  // The pager's scrollLeft can be moved off the column grid by things we
  // don't control: Chromium auto-scrolls it while a selection handle is
  // dragged toward the screen edge, and a cancelled smooth scroll parks it
  // mid-way. Anything left more than a few pixels off its page glides back
  // to the nearest one, so the text always ends up flush with the margins.
  var animUntil = 0;

  function snapCheck() {
    if (R.mode !== 'paged' || !pager || !flow || STEP <= 0) return;
    if (drag && drag.active) return;
    if (Date.now() < animUntil) return;
    var sel = window.getSelection && window.getSelection();
    if (sel && !sel.isCollapsed) return;
    var sl = pager.scrollLeft;
    var p = Math.max(0, Math.min(pageCount() - 1, Math.round(sl / STEP)));
    var want = colScroll(p);
    if (Math.abs(sl - want) > 6) {
      console.log('[pl] snap heal: sl=' + sl + ' want=' + want + ' page=' + p);
      animUntil = Date.now() + 450;
      try { pager.scrollTo({ left: want, behavior: 'smooth' }); }
      catch (e) { pager.scrollLeft = want; }
    } else if (sl !== want) {
    }
  }

  function commitDrag(target, from) {
    var dest = -(target - from) * STEP;
    flow.style.transition = 'transform 230ms ease-out';
    dragTransform(dest);
    setTimeout(function () {
      flow.style.transition = 'none';
      flow.style.transform = '';
      if (pager) {
        var n = pageCount();
        target = Math.max(0, Math.min(n - 1, target));
        pager.scrollLeft = colScroll(target);
        animUntil = Date.now() + 300;
      }
      postMetrics();
    }, 240);
  }

  // ---------- finger-follow page drag ----------

  // While the finger is down in paged mode the whole flow rides with it on a
  // transform; on release the drag's distance and speed decide whether the
  // page turns, then the sheet glides the rest of the way and the scroll
  // position commits under the transform so nothing ever jumps.
  var drag = null;

  function dragTransform(px) {
    flow.style.transform = 'translateX(' + px + 'px)';
  }

  document.addEventListener('touchstart', function (e) {
    if (R.mode !== 'paged' || !pager || !flow) return;
    if (e.touches.length !== 1) return;
    var sel = window.getSelection && window.getSelection();
    if (sel && !sel.isCollapsed) return;
    drag = {
      x: e.touches[0].clientX, y: e.touches[0].clientY,
      t: Date.now(), dx: 0, vx: 0, lx: e.touches[0].clientX, lt: Date.now(),
      page: R.page, active: false, id: e.touches[0].identifier,
    };
  }, { passive: true });

  document.addEventListener('touchmove', function (e) {
    if (!drag || R.mode !== 'paged') return;
    for (var i = 0; i < e.touches.length; i++) {
      var t = e.touches[i];
      if (t.identifier !== drag.id) continue;
      var dx = t.clientX - drag.x, dy = t.clientY - drag.y;
      if (!drag.active) {
        if (Math.abs(dx) < 10 || Math.abs(dx) < Math.abs(dy)) { if (Math.abs(dy) > 14) drag = null; return; }
        drag.active = true;
        flow.style.userSelect = 'none';
        try { window.getSelection().removeAllRanges(); } catch (err) { }
      }
      drag.dx = dx;
      var now = Date.now(), dt = now - drag.lt;
      if (dt > 0) {
        var iv = (t.clientX - drag.lx) / dt;
        drag.vx = drag.vx * 0.7 + iv * 0.3;
        drag.lx = t.clientX; drag.lt = now;
      }
      dragTransform(dx);
    }
  }, { passive: true });

  document.addEventListener('touchend', function (e) {
    if (!drag || !drag.active) { drag = null; return; }
    var d = drag; drag = null;
    flow.style.userSelect = '';
    var w = pager ? pager.clientWidth : window.innerWidth;
    var far = Math.abs(d.dx) > w * 0.26;
    var quick = Math.abs(d.vx) > 0.55 && Math.abs(d.dx) > 40;
    var dir = (d.dx < 0) ? 1 : -1;
    var target = d.page;
    if (far || quick) target = d.page + dir;
    commitDrag(target, d.page);
    // Adopt the target right away so a gesture started during the commit
    // animation builds on the page we're settling on, not the old one.
    var n = pageCount();
    R.page = Math.max(0, Math.min(n - 1, target));
    R.ratio = n > 1 ? R.page / (n - 1) : 0;
    post('metrics', { mode: 'paged', page: R.page + 1, pages: n, chapter: R.chapter, ratio: R.ratio });
  }, { passive: true });

  document.addEventListener('touchcancel', function () {
    if (drag) {
      flow.style.userSelect = '';
      if (drag.active) commitDrag(drag.page, drag.page);
      drag = null;
    }
  }, { passive: true });

  // ---------- metrics ----------

  function currentChapter() {
    var s = secs();
    if (R.mode === 'paged' && pager) {
      var sl = pager.scrollLeft;
      for (var i = s.length - 1; i >= 0; i--) {
        if (s[i].getBoundingClientRect().left + sl <= sl + window.innerWidth * 0.6) return i;
      }
      return 0;
    }
    var vh = window.innerHeight;
    for (var j = s.length - 1; j >= 0; j--) {
      if (s[j].getBoundingClientRect().top <= vh * 0.5) return j;
    }
    return 0;
  }

  function postMetrics() {
    if (R.mode === 'paged' && pager) {
      R.pages = pageCount();
      R.page = STEP > 0 ? Math.max(0, Math.round(pager.scrollLeft / STEP)) : 0;
      R.page = Math.min(R.page, R.pages - 1);
      R.chapter = currentChapter();
      R.ratio = R.pages > 1 ? R.page / (R.pages - 1) : 0;
      post('metrics', { mode: 'paged', page: R.page + 1, pages: R.pages, chapter: R.chapter, ratio: R.ratio });
    } else {
      var max = doc.scrollHeight - window.innerHeight;
      R.ratio = max > 0 ? Math.min(1, Math.max(0, (window.scrollY || 0) / max)) : 0;
      R.chapter = currentChapter();
      post('metrics', { mode: 'scroll', page: 0, pages: 0, chapter: R.chapter, ratio: R.ratio });
    }
  }

  var mT = null;
  function scrollSoon() {
    clearTimeout(mT);
    mT = setTimeout(function () { postMetrics(); snapCheck(); }, 140);
  }
  document.addEventListener('scroll', scrollSoon, { passive: true, capture: true });
  window.addEventListener('scroll', scrollSoon, { passive: true });

  window.addEventListener('resize', function () {
    if (R.mode === 'paged' && pager) { layout(); var r = R.ratio; gotoRatio(r); }
    setTimeout(postMetrics, 60);
  });

  // ---------- tap detection ----------

  var tS = null;
  document.addEventListener('touchstart', function (e) {
    if (e.touches.length === 1) {
      tS = { x: e.touches[0].clientX, y: e.touches[0].clientY, t: Date.now(), moved: false };
    }
  }, { passive: true });
  document.addEventListener('touchmove', function (e) {
    if (tS && e.touches.length === 1) {
      var dx = e.touches[0].clientX - tS.x, dy = e.touches[0].clientY - tS.y;
      if (Math.abs(dx) > 12 || Math.abs(dy) > 12) tS.moved = true;
    }
  }, { passive: true });
  document.addEventListener('touchend', function (e) {
    if (!tS || tS.moved || Date.now() - tS.t > 400) { tS = null; return; }
    var t = e.target;
    if (t && t.closest && t.closest('a')) { tS = null; return; }
    var sel = window.getSelection && window.getSelection();
    if (sel && !sel.isCollapsed && String(sel).length > 0) { tS = null; return; }
    post('tap', { x: tS.x, y: tS.y });
    tS = null;
  }, { passive: true });

  // ---------- selection ----------

  var selT = null;
  document.addEventListener('selectionchange', function () {
    clearTimeout(selT);
    selT = setTimeout(function () {
      var sel = window.getSelection();
      var txt = sel ? String(sel) : '';
      if (txt.length > 0) {
        var rect = null;
        try { rect = sel.getRangeAt(0).getBoundingClientRect(); } catch (e) { }
        post('selection', {
          text: txt.slice(0, 600),
          chapter: currentChapter(),
          top: rect ? rect.top : -1,
          bottom: rect ? rect.bottom : -1,
          cx: rect ? (rect.left + rect.right) / 2 : -1,
        });
      } else {
        post('selection', { text: '' });
        // A selection that just ended may have dragged the pager off-grid
        // (Chromium auto-scrolls while its handles hit the screen edge);
        // bring the page back flush once the handles are gone.
        setTimeout(snapCheck, 380);
      }
    }, 260);
  });

  // ---------- highlights ----------

  var STYLES = { yellow: 1, green: 1, blue: 1, pink: 1, underline: 1 };

  function wrapRange(range, id, style) {
    var span = document.createElement('span');
    span.className = 'pl-hl' + (STYLES[style] && style !== 'yellow' ? ' pl-c-' + style : '');
    span.setAttribute('data-aid', id);
    try {
      var frag = range.extractContents();
      span.appendChild(frag);
      range.insertNode(span);
    } catch (e) { }
  }

  /** Wraps whatever text is selected right now. */
  R.applyHighlight = function (id, style) {
    var sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return;
    var range = sel.getRangeAt(0);
    if (range.collapsed) return;
    wrapRange(range, id, style || 'yellow');
    sel.removeAllRanges();
    setTimeout(postMetrics, 60);
  };

  /** Finds a quote inside one chapter (or the book) and wraps it. */
  R.highlightByText = function (id, text, chapter, style) {
    var root = null;
    var s = secs();
    if (chapter != null && s[chapter]) root = s[chapter];
    var range = root ? findText(root, text) : null;
    if (!range) range = findText(body, text);
    if (range) wrapRange(range, id, style || 'yellow');
    setTimeout(postMetrics, 60);
  };

  /** Restyles every span of one annotation in place. */
  R.setStyle = function (id, style) {
    var els = document.querySelectorAll('span.pl-hl[data-aid="' + id + '"]');
    var cls = 'pl-hl' + (STYLES[style] && style !== 'yellow' ? ' pl-c-' + style : '');
    for (var i = 0; i < els.length; i++) els[i].className = cls;
  };

  R.removeHighlight = function (id) {
    var els = document.querySelectorAll('span.pl-hl[data-aid="' + id + '"]');
    for (var i = els.length - 1; i >= 0; i--) {
      var el = els[i];
      var parent = el.parentNode;
      if (!parent) continue;
      while (el.firstChild) parent.insertBefore(el.firstChild, el);
      parent.removeChild(el);
      parent.normalize();
    }
    setTimeout(postMetrics, 60);
  };

  /** Locates text across text nodes, tolerating whitespace differences. */
  function findText(root, text) {
    if (!text) return null;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
    var nodes = [], map = [], norm = '';
    var n;
    while ((n = walker.nextNode())) {
      var v = n.nodeValue;
      if (!v) continue;
      for (var i = 0; i < v.length; i++) {
        var c = v.charAt(i);
        if (/\s/.test(c)) {
          if (norm.length && norm.charAt(norm.length - 1) !== ' ') {
            norm += ' '; map.push([nodes.length, i]);
          }
        } else {
          norm += c; map.push([nodes.length, i]);
        }
      }
      nodes.push(n);
    }
    var needle = text.replace(/\s+/g, ' ').trim();
    if (!needle) return null;
    var at = norm.indexOf(needle);
    if (at < 0 && needle.length > 60) {
      at = norm.indexOf(needle.slice(0, 60));
      needle = needle.slice(0, 60);
    }
    if (at < 0) return null;
    var end = at + needle.length - 1;
    var a = map[at], b = map[end];
    if (!a || !b) return null;
    var range = document.createRange();
    try {
      range.setStart(nodes[a[0]], a[1]);
      range.setEnd(nodes[b[0]], b[1] + 1);
    } catch (e) { return null; }
    return range;
  }

  // ---------- full-text search ----------

  // Matches are wrapped in spans collected before any mutation and applied
  // from the end of the document backward, so earlier ranges stay valid.
  R.search = function (q) {
    R.searchClear();
    var needle = (q || '').replace(/\s+/g, ' ').trim();
    if (!needle) { post('search', { count: 0 }); return; }
    var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT, null, false);
    var nodes = [], map = [], norm = '';
    var n;
    while ((n = walker.nextNode())) {
      var v = n.nodeValue;
      if (!v) continue;
      for (var i = 0; i < v.length; i++) {
        var c = v.charAt(i);
        if (/\s/.test(c)) {
          if (norm.length && norm.charAt(norm.length - 1) !== ' ') {
            norm += ' '; map.push([nodes.length, i]);
          }
        } else {
          norm += c; map.push([nodes.length, i]);
        }
      }
      nodes.push(n);
    }
    var ranges = [];
    var at = norm.indexOf(needle);
    while (at >= 0 && ranges.length < 300) {
      var a = map[at], b = map[at + needle.length - 1];
      if (a && b) {
        var range = document.createRange();
        try {
          range.setStart(nodes[a[0]], a[1]);
          range.setEnd(nodes[b[0]], b[1] + 1);
          ranges.push(range);
        } catch (e) { }
      }
      at = norm.indexOf(needle, at + needle.length);
    }
    for (var k = ranges.length - 1; k >= 0; k--) {
      var span = document.createElement('span');
      span.className = 'pl-hit';
      span.setAttribute('data-hit', k);
      try {
        var frag = ranges[k].extractContents();
        span.appendChild(frag);
        ranges[k].insertNode(span);
      } catch (e) { }
    }
    post('search', { count: ranges.length });
  };

  R.searchStep = function (i) {
    var el = document.querySelector('span.pl-hit[data-hit="' + i + '"]');
    if (!el) return;
    var prev = document.querySelector('span.pl-hit-on');
    if (prev) prev.className = 'pl-hit';
    el.className = 'pl-hit pl-hit-on';
    if (R.mode === 'paged' && pager && flow) {
      var left = el.getBoundingClientRect().left + pager.scrollLeft;
      gotoPage(Math.max(0, Math.floor(left / STEP)), true);
    } else {
      try { el.scrollIntoView({ block: 'center' }); }
      catch (e) { el.scrollIntoView(); }
    }
  };

  R.searchClear = function () {
    var els = document.querySelectorAll('span.pl-hit');
    for (var i = els.length - 1; i >= 0; i--) {
      var el = els[i];
      var parent = el.parentNode;
      if (!parent) continue;
      while (el.firstChild) parent.insertBefore(el.firstChild, el);
      parent.removeChild(el);
      parent.normalize();
    }
  };

  // ---------- automatic page turning ----------

  var autoT = null;
  R.setAuto = function (ms) {
    if (autoT) { clearInterval(autoT); autoT = null; }
    ms = parseInt(ms, 10);
    if (!ms || ms <= 0) return;
    autoT = setInterval(function () {
      if (R.mode === 'paged' && pager) {
        if (R.page >= pageCount() - 1) {
          R.setAuto(0); post('autoStop', { reason: 'end' }); return;
        }
        gotoPage(R.page + 1, true);
      } else {
        var max = doc.scrollHeight - window.innerHeight;
        if ((window.scrollY || 0) >= max - 4) {
          R.setAuto(0); post('autoStop', { reason: 'end' }); return;
        }
        R.scrollScreens(1);
      }
    }, ms);
  };

  // Any touch of the reader's own hand stops the march.
  document.addEventListener('touchstart', function () {
    if (autoT) { R.setAuto(0); post('autoStop', { reason: 'touch' }); }
  }, { passive: true, capture: true });

  // ---------- commands from the app ----------

  R.setMode = function (m) {
    if (m !== 'paged' && m !== 'scroll') return;
    if (m === R.mode) { if (m === 'paged') layout(); postMetrics(); return; }
    R.mode = m;
    if (m === 'paged') { enablePaged(); gotoRatio(R.ratio); }
    else {
      var r = R.ratio;
      disablePaged();
      var max = doc.scrollHeight - window.innerHeight;
      window.scrollTo(0, r * Math.max(0, max));
    }
    setTimeout(postMetrics, 100);
  };

  R.setFontScale = function (s) {
    body.style.fontSize = (19 * s) + 'px';
    if (R.mode === 'paged' && pager) { layout(); gotoRatio(R.ratio); }
    setTimeout(postMetrics, 80);
  };

  /** Reading theme: one of light / sepia / gray / dark. */
  R.setTheme = function (t) {
    if (t === true) t = 'dark';
    if (t === false || t == null) t = 'light';
    if (['light', 'sepia', 'gray', 'dark'].indexOf(t) < 0) return;
    R.theme = t;
    doc.classList.toggle('pl-dark', t === 'dark');
    doc.classList.toggle('pl-sepia', t === 'sepia');
    doc.classList.toggle('pl-gray', t === 'gray');
  };

  /** Body line height; re-lays out pages because text reflows. */
  R.setLineHeight = function (h) {
    var v = Math.min(2.4, Math.max(1.2, parseFloat(h) || 0));
    if (!v) return;
    body.style.lineHeight = v;
    if (R.mode === 'paged' && pager) { layout(); gotoRatio(R.ratio); }
    setTimeout(postMetrics, 80);
  };

  /** Text face: 'serif' or 'sans'. */
  R.setFontFamily = function (f) {
    body.style.fontFamily = (f === 'sans')
      ? "-apple-system, 'Noto Sans', 'Source Han Sans SC', sans-serif"
      : "Georgia, 'Noto Serif', 'Songti SC', serif";
    if (R.mode === 'paged' && pager) { layout(); gotoRatio(R.ratio); }
    setTimeout(postMetrics, 80);
  };

  /** Scroll mode: slide one screenful up (-1) or down (1). */
  R.scrollScreens = function (n) {
    var d = (n < 0 ? -1 : 1) * window.innerHeight * 0.86;
    try { window.scrollBy({ top: d, behavior: 'smooth' }); }
    catch (e) { window.scrollBy(0, d); }
  };

  function gotoRatio(r) {
    r = Math.min(1, Math.max(0, r));
    if (R.mode === 'paged' && pager) {
      var n = pageCount();
      var p = Math.round(r * (n - 1));
      pager.scrollLeft = colScroll(p);
    } else {
      var max = doc.scrollHeight - window.innerHeight;
      window.scrollTo(0, r * Math.max(0, max));
    }
    setTimeout(postMetrics, 90);
  }
  R.jumpRatio = gotoRatio;

  R.jumpChapter = function (i) {
    var el = secs()[i];
    if (!el) return;
    if (R.mode === 'paged' && pager) {
      var sl = pager.scrollLeft;
      var left = el.getBoundingClientRect().left + sl;
      // Land on the page grid so the chapter opens with full margins.
      pager.scrollLeft = colScroll(Math.max(0, Math.floor(left / STEP)));
    } else {
      var top = el.getBoundingClientRect().top + (window.scrollY || 0);
      window.scrollTo(0, Math.max(0, top - 8));
    }
    setTimeout(postMetrics, 100);
  };

  R.next = function () { if (R.mode === 'paged' && pager) gotoPage(R.page + 1, true); };
  R.prev = function () { if (R.mode === 'paged' && pager) gotoPage(Math.max(0, R.page - 1), true); };

  R.clearSelection = function () {
    var sel = window.getSelection();
    if (sel) sel.removeAllRanges();
  };

  R.getState = function () { postMetrics(); snapCheck(); };
})();
