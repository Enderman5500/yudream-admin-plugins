package online.yudream.base.plugin.tarusso.interfaces.request;

public record StudentMappingSaveRequest(
        Boolean requireBinding,
        String nameKey,
        String deptKey,
        String majorKey,
        String gradeKey,
        String classKey
) {
}
