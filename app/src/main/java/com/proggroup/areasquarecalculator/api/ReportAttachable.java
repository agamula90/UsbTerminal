package com.proggroup.areasquarecalculator.api;

public interface ReportAttachable {
    String reportDate();
    String sampleId();
    String location();
    int countMinutes();
    int volume();
    String operator();
    String date();
}
