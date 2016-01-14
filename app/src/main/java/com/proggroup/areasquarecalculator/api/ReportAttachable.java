package com.proggroup.areasquarecalculator.api;

import java.util.Date;

public interface ReportAttachable {
    Date currentDate();
    String reportDateString();
    String sampleId();
    String location();
    int countMinutes();
    int volume();
    String operator();
    String dateString();
    void writeReport(String reportHtml, String fileName);
    String reportFolders();
}
