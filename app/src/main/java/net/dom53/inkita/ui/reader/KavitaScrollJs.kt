package net.dom53.inkita.ui.reader

/**
 * JS snippet that mirrors Kavita's anchor building for EPUB reader.
 *
 * Usage (later):
 *  webView.addJavascriptInterface(…)
 *  webView.evaluateJavascript(kavitaAnchorScript(), null)
 *
 * It computes an anchor string compatible with Kavita:
 *  - Prefer id("...") for elements with id, plus child index if provided
 *  - Otherwise builds an absolute XPath starting at //body/tag[index]/…
 */
fun kavitaAnchorScript(): String =
    """
(function(){
  function normalizeXPath(xpath){
    var normalized = xpath.toLowerCase();
    var prefixes = ['//html[1]//body','//html[1]//app-root[1]','//html//body','//html//app-root[1]'];
    for(var i=0;i<prefixes.length;i++){
      var prefix = prefixes[i];
      if(normalized.startsWith(prefix)){
        normalized = '//body' + normalized.substring(prefix.length);
        break;
      }
    }
    return normalized;
  }

  function cleanCommonUIPrefixes(xpath){
    var cleaned = xpath;
    cleaned = cleaned.replace(/\/app-root\[\d+\]/g, '');
    if (!cleaned.startsWith('//body') && !cleaned.startsWith('/body')) {
      var bodyIndex = cleaned.indexOf('/body');
      if (bodyIndex !== -1) {
        cleaned = '//' + cleaned.substring(bodyIndex + 1);
      } else {
        cleaned = '//body' + (cleaned.startsWith('/') ? cleaned : '/' + cleaned);
      }
    }
    return cleaned;
  }

  function getXPath(element, pure){
    if(!element) return '';
    if(!pure && element.id){
      return 'id("' + element.id + '")';
    }
    if(element === document.body) return 'body';
    if(!element.parentElement) return element.tagName.toLowerCase();
    var tag = element.tagName.toLowerCase();
    var siblings = Array.from(element.parentElement.children).filter(function(s){ return s.tagName === element.tagName; });
    var idx = siblings.indexOf(element) + 1;
    var parentPath = getXPath(element.parentElement, pure);
    return parentPath ? parentPath + '/' + tag + '[' + idx + ']' : tag + '[' + idx + ']';
  }

  function getXPathTo(element, pure){
    var path = getXPath(element, pure);
    if(path && !path.startsWith('//') && !path.startsWith('id(')){
      path = '//' + path;
    }
    return path;
  }

  function pathFromAncestor(ancestor, element){
    if(!ancestor || !element) return '';
    var segments = [];
    var node = element;
    while(node && node !== ancestor){
      var tag = node.tagName.toLowerCase();
      var siblings = Array.from(node.parentElement.children).filter(function(s){ return s.tagName === node.tagName; });
      var idx = siblings.indexOf(node) + 1;
      segments.unshift(tag + '[' + idx + ']');
      node = node.parentElement;
    }
    return segments.join('/');
  }

  function getAnchor(){
    var el = document.elementFromPoint(window.innerWidth/2, 80) || document.body || document.documentElement;
    if(!el) return "//body";
    var anchorElement = el;
    // Find closest ancestor with id
    var withId = null;
    var probe = anchorElement;
    var depth = 0;
    while(probe && depth < 8){
      if(probe.id){
        withId = probe;
        break;
      }
      probe = probe.parentElement;
      depth++;
    }
    // If no ancestor, try a descendant under the current element
    if(!withId && anchorElement.querySelector){
      var childWithId = anchorElement.querySelector('[id]');
      if(childWithId){
        withId = childWithId;
        anchorElement = childWithId;
      }
    }
    // Fallback: any id on the page
    if(!withId){
      var any = document.querySelector('[id]');
      if(any){
        withId = any;
        anchorElement = any;
      }
    }
    if(withId){
      // If we landed exactly on the id element, prefer a child block (eg. p) for a precise anchor
      if(anchorElement === withId){
        var childCandidate = withId.querySelector('p, h1, h2, h3, h4, h5, h6, div, span');
        if(childCandidate){
          anchorElement = childCandidate;
        }
      }
      var path = pathFromAncestor(withId, anchorElement);
      if(path) return 'id("' + withId.id + '")/' + path;
      return 'id("' + withId.id + '")';
    }
    var xpath = getXPathTo(anchorElement, false);
    xpath = normalizeXPath(xpath);
    xpath = cleanCommonUIPrefixes(xpath);
    return xpath || "//body";
  }

  return { buildAnchor: getAnchor, normalizeXPath: normalizeXPath, cleanCommonUIPrefixes: cleanCommonUIPrefixes, getXPathTo: getXPathTo };
})();
    """.trimIndent()
