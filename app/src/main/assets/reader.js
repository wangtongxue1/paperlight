/*
 * Paperlight reader engine.
 * Loaded into the rendered book page by the app after the document is ready.
 * Talks to Android through the AndroidReader JavascriptInterface:
 *   onEvent(type, json)  with types "tap", "metrics", "selection".
 * Android calls back on window.__pl (setMode, setFontScale, jumpChapter,
 * jumpRatio, next, prev, getState, applyHighlight, highlightByText,
 * removeHighlight, clearSelection).
 */
(function () {
  if (window.__pl) { return; }

  var R = window.__pl = {
    mode: 'scroll',      // 'scroll' | 'paged'
    ratio: 0,            // 0..1 position in the book
    page: 0, pages: 1, chapter: 0,
  };

  var body = document.body;
  var doc = document.documentElement;
  var pager = null, flow = null;
  var PAD = 22;   // horizontal padding inside each column, matches the page's
  var GAP = 48;   // gap between columns = page gutter
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
        'position:fixed;top:0;left:0;right:0;bottom:0;overflow-x:hidden;overflow-y:hidden;';
      while (body.firstChild) flow.appendChild(body.firstChild);
      pager.appendChild(flow);
      body.appendChild(pager);
      body.style.padding = '0';
      doc.style.overflow = 'hidden';
      window.scrollTo(0, 0);
    }
    layout();
  }

  function layout() {
    if (!pager || !flow) return;
    var h = pager.clientHeight, w = pager.clientWidth;
    // The column box lives inside the flow's own horizontal padding, so the
    // usable column width is the viewport minus both pads. Making the column
    // exactly that wide puts one column per screen and keeps every page
    // offset aligned with the scroll math.
    var col = Math.max(100, w - 2 * PAD);
    STEP = col + GAP;
    flow.style.height = h + 'px';
    flow.style.columnWidth = col + 'px';
    flow.style.columnGap = GAP + 'px';
    flow.style.columnFill = 'auto';
    flow.style.padding = '20px ' + PAD + 'px 24px';
  }

  function pageCount() {
    if (!pager || !flow || STEP <= 0) return 1;
    return Math.max(1, Math.round((flow.scrollWidth - 2 * PAD + GAP) / STEP));
  }

  function gotoPage(p) {
    if (!pager) return;
    var n = pageCount();
    p = Math.max(0, Math.min(n - 1, p));
    pager.scrollLeft = PAD + p * STEP;
    setTimeout(postMetrics, 80);
  }

  function disablePaged() {
    if (!pager) return;
    while (flow.firstChild) body.appendChild(flow.firstChild);
    pager.remove();
    pager = null; flow = null;
    body.style.padding = '';
    doc.style.overflow = '';
  }

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
      R.page = STEP > 0 ? Math.max(0, Math.round((pager.scrollLeft - PAD) / STEP)) : 0;
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
  function scrollSoon() { clearTimeout(mT); mT = setTimeout(postMetrics, 140); }
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
      }
    }, 260);
  });

  // ---------- highlights ----------

  function wrapRange(range, id) {
    var span = document.createElement('span');
    span.className = 'pl-hl';
    span.setAttribute('data-aid', id);
    try {
      var frag = range.extractContents();
      span.appendChild(frag);
      range.insertNode(span);
    } catch (e) { }
  }

  /** Wraps whatever text is selected right now. */
  R.applyHighlight = function (id) {
    var sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return;
    var range = sel.getRangeAt(0);
    if (range.collapsed) return;
    wrapRange(range, id);
    sel.removeAllRanges();
    setTimeout(postMetrics, 60);
  };

  /** Finds a quote inside one chapter (or the book) and wraps it. */
  R.highlightByText = function (id, text, chapter) {
    var root = null;
    var s = secs();
    if (chapter != null && s[chapter]) root = s[chapter];
    var range = root ? findText(root, text) : null;
    if (!range) range = findText(body, text);
    if (range) wrapRange(range, id);
    setTimeout(postMetrics, 60);
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
    body.style.fontSize = (17 * s) + 'px';
    if (R.mode === 'paged' && pager) { layout(); gotoRatio(R.ratio); }
    setTimeout(postMetrics, 80);
  };

  function gotoRatio(r) {
    r = Math.min(1, Math.max(0, r));
    if (R.mode === 'paged' && pager) {
      var n = pageCount();
      var p = Math.round(r * (n - 1));
      pager.scrollLeft = PAD + p * STEP;
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
      pager.scrollLeft = Math.max(PAD, left - 2);
    } else {
      var top = el.getBoundingClientRect().top + (window.scrollY || 0);
      window.scrollTo(0, Math.max(0, top - 8));
    }
    setTimeout(postMetrics, 100);
  };

  R.next = function () { if (R.mode === 'paged' && pager) gotoPage(R.page + 1); };
  R.prev = function () { if (R.mode === 'paged' && pager) gotoPage(R.page - 1); };

  R.clearSelection = function () {
    var sel = window.getSelection();
    if (sel) sel.removeAllRanges();
  };

  R.getState = function () { postMetrics(); };
})();
