package com.ikp.dev.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ikp.dev.app.core.common.EpubParser;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** EPUB parser tests with an in-memory fixture EPUB (no binary fixtures). */
public class EpubParserTest {

    private static void put(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] fixtureEpub() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        put(zip, "mimetype", "application/epub+zip".getBytes("UTF-8"));
        put(zip, "META-INF/container.xml", ("<?xml version=\"1.0\"?>"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" "
                + "media-type=\"application/oebps-package+xml\"/></rootfiles></container>").getBytes("UTF-8"));
        put(zip, "OEBPS/content.opf", ("<?xml version=\"1.0\"?>"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<dc:title>Test Book</dc:title><dc:creator>A. Author</dc:creator>"
                + "<dc:language>en</dc:language></metadata>"
                + "<manifest><item id=\"c1\" href=\"ch1.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"c2\" href=\"text/ch2.xhtml\" media-type=\"application/xhtml+xml\"/></manifest>"
                + "<spine><itemref idref=\"c1\"/><itemref idref=\"c2\"/></spine></package>").getBytes("UTF-8"));
        put(zip, "OEBPS/ch1.xhtml", ("<html><head><title>Chapter One</title></head>"
                + "<body><h1>Chapter One</h1><p dir=\"rtl\">النص العربي</p></body></html>").getBytes("UTF-8"));
        put(zip, "OEBPS/text/ch2.xhtml", ("<html><head><title>Chapter Two</title></head>"
                + "<body><p>English text</p></body></html>").getBytes("UTF-8"));
        zip.finish();
        zip.close();
        return out.toByteArray();
    }

    @Test
    public void parsesMetadataAndSpineOrder() throws Exception {
        EpubParser.Epub epub = EpubParser.parse(fixtureEpub());
        assertEquals("Test Book", epub.title);
        assertEquals("A. Author", epub.creator);
        assertEquals("en", epub.language);
        assertEquals(2, epub.sections.size());
        assertEquals("Chapter One", epub.sections.get(0).title);
        assertEquals("OEBPS/ch1.xhtml", epub.sections.get(0).href);
        assertEquals("OEBPS/text/ch2.xhtml", epub.sections.get(1).href);
        assertTrue(epub.sections.get(0).html.contains("النص العربي"));
    }

    @Test
    public void missingContainerFailsCleanly() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(out);
        put(zip, "random.txt", "nope".getBytes("UTF-8"));
        zip.finish();
        zip.close();
        try {
            EpubParser.parse(out.toByteArray());
            throw new AssertionError("expected IOException for non-EPUB zip");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
