package net.dom53.inkita.ui.reader.screen

import org.json.JSONObject

/**
 * Optional "Kindle style" pagination for EPUB content.
 *
 * Kavita serves an EPUB as a list of spine files ("pages"), each of which is usually a whole book chapter.
 * Paged mode keeps the normal vertical layout (so books render exactly as in scroll mode) and moves through
 * it one screenful at a time. Every stop is snapped to a line boundary so text is never cut in half.
 * Native touch scrolling is disabled by the caller while this is active.
 */
internal const val PAGED_BRIDGE_NAME = "InkitaPaged"

/**
 * Installs `window.__ik`. Idempotent; safe to run on every document load.
 *
 * Page numbers are 0-based. The bridge object [PAGED_BRIDGE_NAME] receives `onPage(current, total)`.
 */
internal val PAGED_RUNTIME_JS =
    """
    (function() {
        if (window.__ik) return;
        var ik = window.__ik = {};
        var mask = null;
        var items = null;
        var stops = [0];
        var anchor = null;
        var anchorFrac = 0;
        var timer = null;
        var busy = false;

        function viewHeight() { return Math.max(1, window.innerHeight); }
        function scrollTop() {
            return window.pageYOffset || document.documentElement.scrollTop || 0;
        }
        function docTop(el) { return el.getBoundingClientRect().top + scrollTop(); }

        // Every laid-out text line and image, in reading order, in document coordinates.
        function collect() {
            var out = [];
            var sy = scrollTop();
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
            var range = document.createRange();
            var node;
            while ((node = walker.nextNode())) {
                if (!node.nodeValue || !node.nodeValue.trim()) continue;
                var parent = node.parentElement;
                if (!parent) continue;
                var tag = parent.tagName.toLowerCase();
                if (tag === 'script' || tag === 'style') continue;
                range.selectNodeContents(node);
                var rects = range.getClientRects();
                for (var i = 0; i < rects.length; i++) {
                    if (rects[i].height < 1) continue;
                    out.push({ top: rects[i].top + sy, bottom: rects[i].bottom + sy, el: parent });
                }
            }
            var media = document.body.querySelectorAll('img, svg, video, hr');
            for (var m = 0; m < media.length; m++) {
                var r = media[m].getBoundingClientRect();
                if (r.height >= 1) out.push({ top: r.top + sy, bottom: r.bottom + sy, el: media[m] });
            }
            out.sort(function(a, b) { return a.top - b.top || a.bottom - b.bottom; });
            return out;
        }

        // Room after the last line so the final page can start at a line boundary and end partly empty.
        // Styles are !important and the element is not empty so book CSS (e.g. "div:empty { display: none }")
        // cannot collapse it.
        var spacerExtra = 0;
        function ensureSpacer() {
            var spacer = document.getElementById('ik-spacer');
            if (!spacer) {
                spacer = document.createElement('div');
                spacer.id = 'ik-spacer';
                spacer.appendChild(document.createTextNode('\u00a0'));
                document.body.appendChild(spacer);
            }
            var rules = {
                display: 'block', position: 'static', float: 'none', visibility: 'hidden', overflow: 'hidden',
                margin: '0', padding: '0', border: '0', 'font-size': '0', 'line-height': '0',
                height: (viewHeight() + spacerExtra) + 'px', 'min-height': '0', 'max-height': 'none'
            };
            for (var name in rules) spacer.style.setProperty(name, rules[name], 'important');
        }

        // Scroll to a stop; if the document is too short to reach it, add room and try again.
        function scrollToStop(index) {
            var y = stops[index];
            window.scrollTo(0, y);
            var got = scrollTop();
            if (got < y - 1) {
                spacerExtra += Math.ceil(y - got) + 2;
                ensureSpacer();
                window.scrollTo(0, y);
            }
        }

        function build() {
            ensureSpacer();
            items = collect();
            stops = [0];
            var h = viewHeight();
            var y = 0;
            var guard = 0;
            while (guard++ < 20000) {
                var limit = y + h;
                var next = null;
                var more = false;
                var above = y;
                for (var i = 0; i < items.length; i++) {
                    if (items[i].bottom > limit + 0.5) {
                        more = true;
                        if (items[i].top > y + 1) {
                            // Start between the previous line's bottom and this line's top, so neither the
                            // previous line's descenders nor this line's ascenders end up clipped or visible.
                            // Line boxes can overlap slightly; the boundary then falls in the middle of the overlap.
                            next = Math.round((above + items[i].top) / 2);
                        }
                        break;
                    }
                    if (items[i].bottom > above) above = items[i].bottom;
                }
                if (!more) break;
                if (next === null || next - y < h * 0.4) next = y + h;
                stops.push(next);
                y = next;
            }
            var missing = stops[stops.length - 1] + h - document.documentElement.scrollHeight;
            if (missing > 0) {
                spacerExtra += Math.ceil(missing) + 2;
                ensureSpacer();
            }
        }

        function ensure() { if (!items) build(); }

        function pageForY(y) {
            var page = 0;
            for (var i = 0; i < stops.length; i++) { if (stops[i] <= y + 4) page = i; }
            return page;
        }

        // A page ends at its last whole line; hide everything from the next page's first line down.
        // The mask lives in page coordinates, so it stays glued to the text even if the viewport height or
        // scroll offset is momentarily off by a few pixels.
        function updateMask(page) {
            if (!mask) {
                mask = document.createElement('div');
                mask.style.cssText = 'position:absolute;left:0;width:100%;z-index:2147483647;pointer-events:none;';
                document.documentElement.appendChild(mask);
            }
            if (page >= stops.length - 1) {
                mask.style.display = 'none';
                return;
            }
            var bg = window.getComputedStyle(document.body).backgroundColor;
            if (!bg || bg === 'transparent' || bg.indexOf('rgba(0, 0, 0, 0)') === 0) {
                bg = window.getComputedStyle(document.documentElement).backgroundColor;
            }
            mask.style.background = bg;
            mask.style.top = stops[page + 1] + 'px';
            mask.style.height = viewHeight() + 'px';
            mask.style.display = 'block';
        }

        ik.count = function() { ensure(); return stops.length; };
        ik.current = function() { ensure(); return pageForY(scrollTop()); };

        function report() {
            if (window.$PAGED_BRIDGE_NAME) window.$PAGED_BRIDGE_NAME.onPage(ik.current(), ik.count());
        }

        function firstVisibleElement() {
            ensure();
            var y = scrollTop();
            for (var i = 0; i < items.length; i++) {
                if (items[i].bottom > y + 2) return items[i].el;
            }
            return null;
        }

        // Remember the element at the top and how far into it we are, so a reflow can return to the same text.
        function rememberPosition() {
            var el = firstVisibleElement();
            if (!el) return;
            var rect = el.getBoundingClientRect();
            anchor = el;
            anchorFrac = rect.height > 0 ? Math.max(0, Math.min(0.99, (0 - rect.top) / rect.height)) : 0;
        }

        function anchorY() {
            if (!anchor || !anchor.getBoundingClientRect) return null;
            var rect = anchor.getBoundingClientRect();
            return rect.top + scrollTop() + anchorFrac * rect.height;
        }

        ik.go = function(page) {
            ensure();
            var target = Math.max(0, Math.min(stops.length - 1, page));
            scrollToStop(target);
            updateMask(target);
            rememberPosition();
            report();
        };
        function shift(px, ms, ease, done) {
            var body = document.body;
            body.style.transition = ms ? 'transform ' + ms + 'ms ' + ease : 'none';
            body.style.transform = px === 0 && !ms ? '' : 'translateX(' + px + 'px)';
            if (done) setTimeout(done, ms + 30);
        }

        // Slide the current screen out, swap the content while it is off screen, slide the new one in.
        function turn(direction, target) {
            var width = Math.max(1, window.innerWidth);
            busy = true;
            shift(-direction * width, 110, 'ease-in', function() {
                scrollToStop(target);
                updateMask(target);
                rememberPosition();
                report();
                shift(direction * width, 0);
                void document.body.offsetWidth;
                shift(0, 160, 'ease-out', function() {
                    shift(0, 0);
                    busy = false;
                });
            });
        }

        function move(direction) {
            if (busy) return true;
            var cur = ik.current();
            var target = cur + direction;
            // At the edge of this book page the caller decides what happens (slide to the neighbouring page, or settle).
            if (target < 0 || target > ik.count() - 1) return false;
            turn(direction, target);
            return true;
        }
        ik.next = function() { return move(1); };
        ik.prev = function() { return move(-1); };

        // Follow a finger: dx is the horizontal drag distance in CSS px.
        // canPrev / canNext say whether another book page exists beyond this one; only without one does it resist.
        ik.drag = function(dx, canPrev, canNext) {
            if (busy) return;
            var cur = ik.current();
            var blocked = (dx > 0 && cur === 0 && !canPrev) || (dx < 0 && cur === ik.count() - 1 && !canNext);
            shift(blocked ? dx * 0.25 : dx, 0);
        };

        // Slide this book page away while the next one is loaded; enter() then slides the new one in.
        ik.leave = function(direction) {
            busy = true;
            shift(-direction * Math.max(1, window.innerWidth), 110, 'ease-in');
        };
        ik.enter = function(direction) {
            busy = true;
            shift(direction * Math.max(1, window.innerWidth), 0);
            void document.body.offsetWidth;
            shift(0, 170, 'ease-out', function() {
                shift(0, 0);
                busy = false;
            });
        };
        // The next page never arrived: bring this one back.
        ik.recover = function() {
            busy = false;
            shift(0, 150, 'ease-out', function() { shift(0, 0); });
        };
        ik.settle = function() {
            if (busy) return;
            shift(0, 150, 'ease-out', function() { shift(0, 0); });
        };
        ik.last = function() { ik.go(ik.count() - 1); };

        // Token stored as reading progress: an XPath id() expression, or idx:<n> into body *.
        ik.anchorToken = function() {
            var el = firstVisibleElement();
            if (!el) return null;
            if (el.id) return 'id(' + JSON.stringify(el.id) + ')';
            var index = Array.prototype.indexOf.call(document.body.querySelectorAll('*'), el);
            return index >= 0 ? 'idx:' + index : null;
        };

        function resolve(token) {
            if (!token) return null;
            if (token.indexOf('idx:') === 0) {
                return document.body.querySelectorAll('*')[parseInt(token.substring(4), 10)] || null;
            }
            try {
                var res = document.evaluate(token, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                if (res.singleNodeValue) return res.singleNodeValue;
            } catch (e) {}
            var plain = token.replace(/^id\(["']/, '').replace(/["']\)${'$'}/, '');
            return document.getElementById(plain);
        }

        ik.goToToken = function(token) {
            var el = resolve(token);
            if (!el || !el.getBoundingClientRect) return false;
            ensure();
            ik.go(pageForY(docTop(el)));
            anchor = el;
            anchorFrac = 0;
            return true;
        };

        // Call after anything that can change the layout (style change, fonts, images, resize).
        ik.relayout = function() {
            var y = anchorY();
            build();
            var page = y === null ? pageForY(scrollTop()) : pageForY(y);
            var target = Math.max(0, Math.min(stops.length - 1, page));
            scrollToStop(target);
            updateMask(target);
            report();
        };

        function schedule() {
            if (timer) clearTimeout(timer);
            timer = setTimeout(function() {
                if (busy) { schedule(); return; }
                ik.relayout();
            }, 80);
        }

        // If something nudges the scroll position off a page boundary, put it back.
        var healTimer = null;
        function heal() {
            if (busy || !items) return;
            var y = scrollTop();
            var best = 0;
            var distance = Infinity;
            for (var i = 0; i < stops.length; i++) {
                var d = Math.abs(stops[i] - y);
                if (d < distance) { distance = d; best = i; }
            }
            if (distance > 1.5 && distance < viewHeight() / 2) {
                scrollToStop(best);
                updateMask(best);
                report();
            }
        }

        ik.start = function() {
            // The slide animation briefly makes the page wider than the screen; never let that scroll.
            document.documentElement.style.overflowX = 'hidden';
            var imgs = document.images;
            for (var i = 0; i < imgs.length; i++) {
                if (!imgs[i].complete) imgs[i].addEventListener('load', schedule);
            }
            if (document.fonts && document.fonts.ready) document.fonts.ready.then(schedule);
            if (document.fonts && document.fonts.addEventListener) document.fonts.addEventListener('loadingdone', schedule);
            window.addEventListener('load', schedule);
            window.addEventListener('resize', schedule);
            if (window.ResizeObserver) {
                var observer = new ResizeObserver(schedule);
                observer.observe(document.body);
                observer.observe(document.documentElement);
            }
            window.addEventListener('scroll', function() {
                if (healTimer) clearTimeout(healTimer);
                healTimer = setTimeout(heal, 200);
            }, { passive: true });
            report();
        };
    })();
    """.trimIndent()

/** Script run once per loaded document: install the runtime, then place the reader. */
internal fun pagedStartJs(
    scrollToken: String?,
    atEnd: Boolean,
    enterDirection: Int = 0,
): String {
    val token = scrollToken?.let { JSONObject.quote(it) } ?: "null"
    return """
        $PAGED_RUNTIME_JS
        (function() {
            var ik = window.__ik;
            ik.start();
            if (!($token !== null && ik.goToToken($token))) {
                if ($atEnd) ik.last(); else ik.go(0);
            }
            if ($enterDirection !== 0) ik.enter($enterDirection);
        })();
        """.trimIndent()
}

internal fun pagedGoToTokenJs(token: String): String = "(window.__ik && window.__ik.goToToken(${JSONObject.quote(token)}));"
