package com.ikp.dev.app.core.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Minimal EPUB parser (pure java.util.zip — JVM-testable, no external
 * dependency). EPUB 2/3: ZIP + META-INF/container.xml → OPF → spine order.
 * Rendering happens in a WebView with the app's reader themes (RTL CSS there).
 */
public final class EpubParser {

    private EpubParser() {
    }

    public static final class Section {
        public final String title;
        public final String href;
        public final String html;

        public Section(String title, String href, String html) {
            this.title = title == null ? "" : title;
            this.href = href == null ? "" : href;
            this.html = html == null ? "" : html;
        }
    }

    public static final class Epub {
        public final String title;
        public final String creator;
        public final String language;
        public final List<Section> sections;

        public Epub(String title, String creator, String language, List<Section> sections) {
            this.title = title == null ? "" : title;
            this.creator = creator == null ? "" : creator;
            this.language = language == null ? "" : language;
            this.sections = java.util.Collections.unmodifiableList(sections);
        }
    }

    public static Epub parse(File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Map<String, byte[]> entries = new HashMap<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) entries.put(e.getName(), readAll(zip.getInputStream(e)));
            }
            return parseFromMap(entries);
        }
    }

    public static Epub parse(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                entries.put(e.getName(), out.toByteArray());
            }
        }
        return parseFromMap(entries);
    }

    private static Epub parseFromMap(Map<String, byte[]> entries) throws IOException {
        String container = text(entries, "META-INF/container.xml");
        if (container == null) throw new IOException("not an EPUB: META-INF/container.xml missing");
        String opfPath = extractAttr(container, "full-path");
        if (opfPath == null) throw new IOException("EPUB container.xml has no rootfile full-path");
        String opf = text(entries, opfPath);
        if (opf == null) throw new IOException("EPUB OPF document missing: " + opfPath);
        String opfDir = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

        String title = extractTagText(opf, "dc:title");
        String creator = extractTagText(opf, "dc:creator");
        String language = extractTagText(opf, "dc:language");

        Map<String, String> manifest = new HashMap<>();
        Matcher m = Pattern.compile("<item\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(opf);
        while (m.find()) {
            String id = extractAttrFrom(m.group(), "id");
            String href = extractAttrFrom(m.group(), "href");
            if (id != null && href != null) manifest.put(id, href);
        }

        List<Section> sections = new ArrayList<>();
        Matcher sm = Pattern.compile("<itemref\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(opf);
        while (sm.find()) {
            String idref = extractAttrFrom(sm.group(), "idref");
            String href = idref == null ? null : manifest.get(idref);
            if (href == null) continue;
            String resolved = resolve(opfDir, href);
            String html = text(entries, resolved);
            if (html == null) continue;
            String secTitle = extractTagText(html, "title");
            if (secTitle == null || secTitle.isBlank()) secTitle = lastPathSegment(href);
            sections.add(new Section(secTitle, resolved, html));
        }
        if (sections.isEmpty()) throw new IOException("EPUB has no spine items");
        return new Epub(title, creator, language, sections);
    }

    private static String resolve(String dir, String href) {
        String clean = href;
        int hash = clean.indexOf('#');
        if (hash >= 0) clean = clean.substring(0, hash);
        while (clean.startsWith("./")) clean = clean.substring(2);
        return dir + clean;
    }

    private static String lastPathSegment(String path) {
        String p = path;
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    private static String text(Map<String, byte[]> entries, String name) {
        byte[] b = entries.get(name);
        if (b == null) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    b = e.getValue();
                    break;
                }
            }
        }
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    private static String extractTagText(String xml, String tag) {
        Matcher m = Pattern.compile("<" + tag + "\\b[^>]*>(.*?)</" + tag + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        if (!m.find()) return null;
        String v = m.group(1).replaceAll("<[^>]+>", "").trim();
        return v.isEmpty() ? null : decode(v);
    }

    private static String extractAttr(String xml, String attr) {
        Matcher m = Pattern.compile(attr + "\\s*=\\s*\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE).matcher(xml);
        return m.find() ? decode(m.group(1)) : null;
    }

    private static String extractAttrFrom(String tag, String attr) {
        Matcher m = Pattern.compile(attr + "\\s*=\\s*\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? decode(m.group(1)) : null;
    }

    private static String decode(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
