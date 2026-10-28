package com.lifetracker.statement;

import java.util.List;

public class ExtractedText {
    private List<ExtractedLine> lines;
    private String method;
    private int pageCount;

    public ExtractedText() {
    }

    public ExtractedText(List<ExtractedLine> lines, String method, int pageCount) {
        this.lines = lines;
        this.method = method;
        this.pageCount = pageCount;
    }

    public List<ExtractedLine> getLines() {
        return lines;
    }

    public void setLines(List<ExtractedLine> lines) {
        this.lines = lines;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }
}
