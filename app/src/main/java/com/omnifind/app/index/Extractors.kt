package com.omnifind.app.index

import android.text.Html
import android.util.Xml
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

/** A searchable piece of a file: a PDF page (`loc` = its number) or a document's chunk (`loc` = its index from 0). */
data class Piece(val kind: SegmentKind, val loc: Int, val text: String)

/** What reading a file found: its pieces, a title worth more than the file name, and facts for its `info`. */
data class Extracted(val pieces: List<Piece>, val title: String? = null, val pages: Int? = null)

/** Called for each XML event with the tag it's inside (for text: the element holding it). */
private typealias XmlVisitor = (parser: XmlPullParser, parent: String?, out: StringBuilder) -> Unit

/** Turns a file into text pieces. Throws for a file it can't read (it's marked failed; its neighbours go on). */
object Extractors {
    /** Only machine-made giants get this far: pages and chunks past these are left out. */
    const val MAX_PAGES = 5_000
    const val MAX_CHUNKS = 5_000

    fun extract(file: File, kind: FileKind): Extracted = when (kind) {
        FileKind.PDF -> pdf(file)
        FileKind.DOC -> {
            val text = when (file.extension.lowercase()) {
                "html", "htm" -> html(readText(file))
                "docx" -> docx(file)
                "odt" -> odt(file)
                else -> readText(file)
            }
            val chunks = Chunker.chunks(text, limit = MAX_CHUNKS)
            Extracted(chunks.mapIndexed { index, chunk -> Piece(SegmentKind.CHUNK, index, chunk) })
        }
        FileKind.IMAGE, FileKind.SCREENSHOT -> throw IllegalArgumentException("pictures are read by Pictures")
    }

    /** UTF-8 when it is; otherwise Windows-1252, which reads any byte (old notes from Windows). */
    fun readText(file: File): String {
        val bytes = file.readBytes()
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
    }

    private val SCRIPTS = Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>")

    fun html(source: String): String =
        Html.fromHtml(source.replace(SCRIPTS, " "), Html.FROM_HTML_MODE_LEGACY).toString()

    /** Word's text: paragraphs (w:p) as lines, w:tab as tabs, w:br as line breaks. */
    fun docx(file: File): String = zipXml(file, "word/document.xml") { parser, parent, out ->
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "w:tab" -> out.append('\t')
                "w:br", "w:cr" -> out.append('\n')
            }
            XmlPullParser.END_TAG -> if (parser.name == "w:p") out.append('\n')
            XmlPullParser.TEXT -> if (parent == "w:t") out.append(parser.text)
        }
    }

    /** OpenDocument's text: text:p and text:h as lines, text:s as spaces, text:tab as tabs. */
    fun odt(file: File): String = zipXml(file, "content.xml") { parser, _, out ->
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "text:s" -> out.append(" ".repeat(parser.getAttributeValue(null, "text:c")?.toIntOrNull() ?: 1))
                "text:tab" -> out.append('\t')
                "text:line-break" -> out.append('\n')
            }
            XmlPullParser.END_TAG -> if (parser.name == "text:p" || parser.name == "text:h") out.append('\n')
            XmlPullParser.TEXT -> out.append(parser.text)
        }
    }

    private fun zipXml(file: File, entry: String, visit: XmlVisitor): String =
        ZipFile(file).use { zip ->
            val xml = zip.getEntry(entry) ?: throw IllegalStateException("no $entry inside")
            zip.getInputStream(xml).use { parse(it, visit) }
        }

    private fun parse(input: InputStream, visit: XmlVisitor): String {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val out = StringBuilder()
        val tags = ArrayDeque<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> tags.addLast(parser.name)
                XmlPullParser.END_TAG -> tags.removeLastOrNull()
            }
            visit(parser, tags.lastOrNull(), out)
        }
        return out.toString()
    }

    /**
     * A PDF's text, page by page. Its title comes from the PDF's own metadata when that's a real title. Kept in a temp
     * file rather than memory: a big PDF must not exhaust a 4 GB phone. Scans have no text and are skipped for now
     * (reading them as pictures comes with milestone 3).
     */
    fun pdf(file: File): Extracted =
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { document ->
            val count = minOf(document.numberOfPages, MAX_PAGES)
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val pieces = (1..count).mapNotNull { page ->
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(document).trim()
                if (text.isEmpty()) null else Piece(SegmentKind.PAGE, page, text)
            }
            val title = document.documentInformation?.title?.trim()?.takeIf { it.length in 4..200 }
            Extracted(pieces, title = title, pages = document.numberOfPages)
        }
}
