package com.lifetracker.fitness.dto;

import java.util.List;

public class RangeResponse {
    private List<RangeDayResponse> days;
    private RangeAveragesResponse averages;

    public RangeResponse() {
    }

    public RangeResponse(List<RangeDayResponse> days, RangeAveragesResponse averages) {
        this.days = days;
        this.averages = averages;
    }

    public List<RangeDayResponse> getDays() { return days; }
    public void setDays(List<RangeDayResponse> days) { this.days = days; }

    public RangeAveragesResponse getAverages() { return averages; }
    public void setAverages(RangeAveragesResponse averages) { this.averages = averages; }
}
