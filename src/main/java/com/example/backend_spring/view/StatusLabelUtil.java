package com.example.backend_spring.view;

public final class StatusLabelUtil {

    private StatusLabelUtil() {
    }

    public static String toJa(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        switch (status) {
            case "IN_PROGRESS":
                return "進行中";
            case "DONE":
                return "完了";
            case "PENDING":
                return "未着手";
            case "ACTIVE":
                return "対応中";
            case "CONFIRMED":
                return "確定";
            case "SKIPPED":
                return "スキップ";
            case "PROPOSED":
                return "候補";
            case "SELECTED":
                return "選択済み";
            case "REJECTED":
                return "却下";
            default:
                return status;
        }
    }
}
