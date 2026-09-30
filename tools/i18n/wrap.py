"""Wraps user-facing Kotlin string literals in L(...). Templates become format strings.

Usage: wrap.py [--dry] file...
"""
import re, sys

SKIP_BEFORE = [
    'Regex(', 'displayPattern(', 'ofPattern(', 'PreferencesKey(', 'putExtra(', 'getStringExtra(',
    'getBooleanExtra(', 'getIntExtra(', 'getLongExtra(', 'Intent(', 'setAction(', 'parse(', 'setType(',
    'CreateDocument(', 'navigate(', 'composable(', 'Log.', 'execSQL(', 'query(', 'optString(',
    'optJSONObject(', 'optInt(', 'optLong(', 'optBoolean(', 'getJSONObject(', 'getString(', 'getBoolean(',
    'getInt(', 'has(', 'startsWith(', 'endsWith(', 'split(', 'replace(', 'removePrefix(', 'removeSuffix(',
    'substringBefore(', 'substringAfter(', 'substringBeforeLast(', 'substringAfterLast(', 'indexOf(',
    'lastIndexOf(', 'L(', 'getInstance(', 'forLanguageTag(', 'NotificationChannel(', 'channelId',
    'createDocument(', 'stringResource(', 'Route', 'route', 'EXTRA_', 'arrayOf("application', 'mime',
    'contentType', 'KeyStore', 'Cipher', 'Mac.', 'SecretKey', 'MessageDigest', 'TimeZone', 'ZoneId.of(',
    'semantics.', 'testTag(', 'key = ', 'fileName(', 'File(', 'Uri.', 'withLink', 'LinkAnnotation',
    'Charsets', 'String.format(', '.format(', 'sqlEscape', 'SELECT', 'DELETE', 'UPDATE', 'INSERT',
]
SKIP_LINE_PREFIX = ('import ', 'package ', '@', 'const val', 'private const val', 'internal const val', '//', '*', '/*')

STORED_VALUES = {'Tk', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun', 'Violet'}

def user_facing(static_text: str) -> bool:
    t = static_text
    if t.strip() in STORED_VALUES:
        return False
    if re.match(r'\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|PRAGMA|WITH)\b', t):
        return False
    if not re.search(r'[A-Za-z]{2}', t):
        return False
    if re.fullmatch(r'[a-z0-9_./:\-]+', t):   # keys, routes, ids, file names
        return False
    if re.fullmatch(r'[A-Z0-9_]+', t):        # ENUM_NAMES, keys
        return False
    if t.startswith(('http', 'content:', 'package:', 'android.', 'com.', 'androidx.', 'application/', 'text/', 'image/')):
        return False
    if re.search(r'%0?\d*[dsx]', t) and ' ' not in t:
        return False
    return ' ' in t or re.match(r'^[A-Z][a-z]', t) is not None or re.match(r'^[A-Z][A-Z][a-z]', t) is not None

class Lexer:
    def __init__(self, src):
        self.s = src
        self.n = len(src)

    def parse_string(self, i):
        """At an opening '"' (not raw). Returns (end_index_after_closing_quote, parts) where parts is
        a list of ('text', raw_text) / ('expr', expr_source_or_ident, is_braced)."""
        assert self.s[i] == '"'
        i += 1
        parts = []
        buf = []
        s = self.s
        while i < self.n:
            c = s[i]
            if c == '\\':
                buf.append(s[i:i+2]); i += 2; continue
            if c == '"':
                if buf: parts.append(('text', ''.join(buf)))
                return i + 1, parts
            if c == '$':
                if i + 1 < self.n and s[i+1] == '{':
                    if buf: parts.append(('text', ''.join(buf))); buf = []
                    j = i + 2
                    depth = 1
                    while j < self.n and depth:
                        cj = s[j]
                        if cj == '"':
                            if s.startswith('"""', j):
                                j = s.index('"""', j + 3) + 3; continue
                            j, _ = self.parse_string(j); continue
                        if cj == "'":
                            j = self.skip_char(j); continue
                        if cj == '{': depth += 1
                        elif cj == '}': depth -= 1
                        j += 1
                    parts.append(('expr', s[i+2:j-1], True))
                    i = j
                    continue
                m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', s[i+1:])
                if m:
                    if buf: parts.append(('text', ''.join(buf))); buf = []
                    parts.append(('expr', m.group(0), False))
                    i += 1 + len(m.group(0))
                    continue
            buf.append(c); i += 1
        raise ValueError('unterminated string')

    def skip_char(self, i):
        # 'x' or '\n' or '\u0000'
        j = i + 1
        if self.s[j] == '\\':
            j += 2
            while j < self.n and self.s[j] != "'": j += 1
            return j + 1
        return j + 2

def transform(src, stats):
    lx = Lexer(src)
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i); j = n if j < 0 else j
            out.append(src[i:j]); i = j; continue
        if src.startswith('/*', i):
            depth = 0; j = i
            while j < n:
                if src.startswith('/*', j): depth += 1; j += 2; continue
                if src.startswith('*/', j):
                    depth -= 1; j += 2
                    if depth == 0: break
                    continue
                j += 1
            out.append(src[i:j]); i = j; continue
        if src.startswith('"""', i):
            j = src.index('"""', i + 3) + 3
            while j < n and src[j] == '"': j += 1
            out.append(src[i:j]); i = j; continue
        if c == "'":
            j = lx.skip_char(i); out.append(src[i:j]); i = j; continue
        if c == '"':
            end, parts = lx.parse_string(i)
            out.append(rewrite_literal(src, i, end, parts, stats))
            i = end; continue
        out.append(c); i += 1
    return ''.join(out)

def rewrite_literal(src, start, end, parts, stats):
    original = src[start:end]
    line_start = src.rfind('\n', 0, start) + 1
    line_end = src.find('\n', end); line_end = len(src) if line_end < 0 else line_end
    before = src[line_start:start]
    after = src[end:line_end]
    stripped = before.lstrip()
    # recurse into template expressions first
    new_parts = []
    for p in parts:
        if p[0] == 'expr' and p[2]:
            new_parts.append(('expr', transform(p[1], stats), True))
        else:
            new_parts.append(p)
    static = ''.join(p[1] for p in new_parts if p[0] == 'text')
    context_skip = (
        stripped.startswith(SKIP_LINE_PREFIX)
        or any(tok in before[-40:] for tok in SKIP_BEFORE)
        or re.match(r'\s*->', after) is not None
        or re.search(r'(==|!=)\s*$', before) is not None
        or re.match(r'\s*(==|!=|\.toRegex|\.format\(|\.split|\.toInt|\.toLong)', after) is not None
        or re.search(r'\bL\($', before.rstrip()) is not None
    )
    def rebuild(ps):
        o = '"'
        for p in ps:
            if p[0] == 'text': o += p[1]
            elif p[2]: o += '${' + p[1] + '}'
            else: o += '$' + p[1]
        return o + '"'
    caps_label = (
        re.fullmatch(r"[A-Z][A-Z &'’·/,.\-]*[A-Z]", static.strip()) is not None and '_' not in static
        and re.search(r'(Text\(|Group\(|Label\(|Header\(|label = |title = |text = )\s*$', before) is not None
    )
    if context_skip or not (user_facing(static) or caps_label):
        return rebuild(new_parts)
    exprs = [p for p in new_parts if p[0] == 'expr']
    if not exprs:
        stats['plain'] += 1
        return 'L(' + original + ')'
    fmt = '"'
    args = []
    k = 0
    for p in new_parts:
        if p[0] == 'text':
            fmt += p[1].replace('%', '%%')
        else:
            k += 1
            fmt += '%' + str(k) + '\\$s'
            args.append(p[1] if not p[2] else p[1].strip())
    fmt += '"'
    stats['template'] += 1
    return 'L(' + fmt + ', ' + ', '.join('(' + a + ')' if (' ' in a or 'if' in a) else a for a in args) + ')'

if __name__ == '__main__':
    dry = '--dry' in sys.argv
    files = [a for a in sys.argv[1:] if a != '--dry']
    total = {'plain': 0, 'template': 0}
    for f in files:
        src = open(f, encoding='utf-8').read()
        stats = {'plain': 0, 'template': 0}
        out = transform(src, stats)
        if out != src:
            if 'import com.example.timeapp.data.L\n' not in out and not re.search(r'^package com\.example\.timeapp\.data$', out, re.M):
                out = re.sub(r'^(package \S+\n)', r'\1\nimport com.example.timeapp.data.L\n', out, count=1, flags=re.M)
            if not dry:
                open(f, 'w', encoding='utf-8').write(out)
        total['plain'] += stats['plain']; total['template'] += stats['template']
        if dry: print(f, stats)
    print('TOTAL', total)
