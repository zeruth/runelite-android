/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.awt.impl;

import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/**
 * The job {@link PrinterJob#getPrinterJob()} returns on Android: the "no printers available"
 * job the spec describes. The dialogs are answered as cancelled, page formats are the
 * {@link java.awt.print.Paper} defaults (US Letter, one-inch margins) and {@link #print()}
 * fails with {@link PrinterException}, which is what a desktop job without a service does.
 * The job's own state (pages, copies, name) is real so callers that set it
 * up before asking the dialog read back what they wrote.
 */
public final class HeadlessPrinterJob extends PrinterJob {
    private Pageable document;
    private int copies = 1;
    private String jobName = "Java Printing";

    @Override
    public void setPrintable(Printable painter) {
        setPrintable(painter, defaultPage());
    }

    @Override
    public void setPrintable(Printable painter, PageFormat format) {
        Book book = new Book();
        book.append(painter, format);
        document = book;
    }

    @Override
    public void setPageable(Pageable document) throws NullPointerException {
        if (document == null) {
            throw new NullPointerException();
        }
        this.document = document;
    }

    /** Pageable most recently installed, for tests; null before the first set call. */
    public Pageable getPageable() {
        return document;
    }

    @Override
    public boolean printDialog() {
        return false;
    }

    @Override
    public PageFormat pageDialog(PageFormat page) {
        return page;
    }

    @Override
    public PageFormat defaultPage(PageFormat page) {
        // As desktop's RasterPrinterJob: the clone is reset to portrait on the default paper.
        PageFormat result = (PageFormat) page.clone();
        result.setOrientation(PageFormat.PORTRAIT);
        result.setPaper(new java.awt.print.Paper());
        return result;
    }

    @Override
    public PageFormat validatePage(PageFormat page) {
        // No printer constrains the imageable area, so any page the caller built is valid.
        return (PageFormat) page.clone();
    }

    @Override
    public void print() throws PrinterException {
        throw new PrinterException("no print service");
    }

    @Override
    public void setCopies(int copies) {
        this.copies = copies < 1 ? 1 : copies;
    }

    @Override
    public int getCopies() {
        return copies;
    }

    @Override
    public String getUserName() {
        return System.getProperty("user.name");
    }

    @Override
    public void setJobName(String jobName) {
        if (jobName == null) {
            throw new NullPointerException();
        }
        this.jobName = jobName;
    }

    @Override
    public String getJobName() {
        return jobName;
    }

    // A job is never in progress, print() fails before it starts one, and the spec makes cancel
    // without a job in progress a no-op that leaves isCancelled false.
    @Override
    public void cancel() {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
