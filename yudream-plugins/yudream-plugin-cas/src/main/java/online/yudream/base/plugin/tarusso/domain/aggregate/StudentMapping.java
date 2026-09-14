package online.yudream.base.plugin.tarusso.domain.aggregate;

/**
 * 学生信息映射与访问控制配置。
 * <p>塔里木大学 CAS 返回的属性字段名以学校端配置为准，插件无法预知，
 * 因此每个目标字段（姓名/学院/专业/年级）支持显式指定属性键；
 * 留空时按常见键名自动探测。requireBinding 是「未绑定 CAS 禁用其他功能」的开关，
 * 仅在登录入口就绪时生效，避免把管理员锁死。</p>
 */
public final class StudentMapping {

    private final boolean requireBinding;
    private final String nameKey;
    private final String deptKey;
    private final String majorKey;
    private final String gradeKey;
    private final String classKey;

    public StudentMapping(
            boolean requireBinding,
            String nameKey,
            String deptKey,
            String majorKey,
            String gradeKey,
            String classKey
    ) {
        this.requireBinding = requireBinding;
        this.nameKey = trimToEmpty(nameKey);
        this.deptKey = trimToEmpty(deptKey);
        this.majorKey = trimToEmpty(majorKey);
        this.gradeKey = trimToEmpty(gradeKey);
        this.classKey = trimToEmpty(classKey);
    }

    public static StudentMapping defaults() {
        return new StudentMapping(false, "", "", "", "", "");
    }

    public boolean requireBinding() {
        return requireBinding;
    }

    public String nameKey() {
        return nameKey;
    }

    public String deptKey() {
        return deptKey;
    }

    public String majorKey() {
        return majorKey;
    }

    public String gradeKey() {
        return gradeKey;
    }

    public String classKey() {
        return classKey;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
