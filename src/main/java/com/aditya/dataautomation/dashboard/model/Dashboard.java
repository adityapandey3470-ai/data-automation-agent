package com.aditya.dataautomation.dashboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Dashboard {

    private String dashboardId;
    private Long userId;
    private String fileId;
    private String fileName;
    private String name;
    private String sheetName;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<DashboardWidget> widgets = new CopyOnWriteArrayList<>();

    @Builder.Default
    private List<String> skippedWidgets = new CopyOnWriteArrayList<>();

    public List<DashboardWidget> getWidgets() {
        if (widgets == null) {
            widgets = new CopyOnWriteArrayList<>();
        }
        return widgets;
    }

    public void addWidget(DashboardWidget widget) {
        if (widgets == null) {
            widgets = new CopyOnWriteArrayList<>();
        }
        widgets.add(widget);
    }

    public void addSkippedWidget(String message) {
        if (skippedWidgets == null) {
            skippedWidgets = new CopyOnWriteArrayList<>();
        }
        skippedWidgets.add(message);
    }

    public boolean removeWidget(String widgetId) {
        if (widgets == null || widgetId == null) return false;
        return widgets.removeIf(w -> widgetId.equals(w.getWidgetId()));
    }

    public DashboardWidget getWidget(String widgetId) {
        if (widgets == null || widgetId == null) return null;
        return widgets.stream()
                .filter(w -> widgetId.equals(w.getWidgetId()))
                .findFirst()
                .orElse(null);
    }
}
