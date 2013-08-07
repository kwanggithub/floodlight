package org.projectfloodlight.db.tools.docgen;

import java.io.BufferedWriter;
import java.io.IOException;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.tools.codegen.TextWriter;

/**
 * A helper class to write the contents into wiki format.
 * Confluence wiki markup file is a text file with markup
 * and macros.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class ConfluenceWikiWriter
    extends TextWriter implements IDocFormatter{

    @Override
    public String getSkippedString(String text) {
        return text.replace("]", "\\]").replace("|", "\\|");
    }
    public ConfluenceWikiWriter(String path)
            throws BigDBException {
        super(path);
    }

    @Override
    public void emitTableHeader(String[] headers)
            throws BigDBException {
        this.emitTableRow(headers, "||");
    }

    @Override
    public void emitTableRow(String[] items)
            throws BigDBException {
        this.emitTableRow(items, "|");
    }

    @Override
    public void emitTableRow(String[] headers, String sep)
            throws BigDBException {
        StringBuilder sb = new StringBuilder();
        for (String s : headers) {
            sb.append(sep + (s.isEmpty() ? " " : s));
        }
        if (headers.length > 0) {
            sb.append(sep);
        }
        this.emitLine(0, sb.toString(), writer);
    }


    @Override
    public void emitTabs(int number, BufferedWriter out)
            throws BigDBException {
        try {
            if (number <= 0)
                return;
            for (int i = 0; i < number; ++i) {
                out.append("*");
            }
            out.flush();
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }

    @Override
    public String getStringWithLink(String s, String link) {
        return "[" + s + "|#" + link + "]";
    }

    @Override
    public void emitHeaderWithLink(int level, int tabs,
                                   String header, String link)
            throws BigDBException {
        this.emitLine(tabs, " h" + Integer.toString(3+level) + ". " +
                            this.getStringWithLink(header, link),
                      writer);
    }

    @Override
    public void emitHeader(int level, int tabs, String header)
            throws BigDBException {
        this.emitLine(tabs, " h" + Integer.toString(3+level) + ". " + header,
                      writer);
    }

    @Override
    public void emitText(int tabs, String text, boolean endLine)
            throws BigDBException {
        if (text != null && !text.isEmpty()) {
            // process the text for format
            String endLineRegex = "((\\n|\\r|\\r\\n)\\s*){2}";
            text = text.replaceAll(endLineRegex, "\\\\\\\\ \\\\\\\\");
            endLineRegex = "(\\n|\\r|\\r\\n)\\s*";
            text = text.replaceAll(endLineRegex, " ");
            this.emitLineFirstPart(0, text, writer);
            if (endLine) {
                this.emitRestOfLine("\\\\", writer);
            }
        }
    }

    @Override
    public void emitText(int tabs, String text)
            throws BigDBException {
        emitText(tabs, text, true);
    }

    @Override
    public void emitCodeTextWithExpand(String name, String text)
            throws BigDBException {
        emitText(2, "{expand:sample" + name + "}{code}" + text + "{code}{expand}");

    }
    @Override
    public void emitSectionEnd() throws BigDBException {
        this.emitLine(0, "", writer);
    }
}
