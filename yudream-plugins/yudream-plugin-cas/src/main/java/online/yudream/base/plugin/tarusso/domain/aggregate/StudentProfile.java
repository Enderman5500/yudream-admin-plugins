package online.yudream.base.plugin.tarusso.domain.aggregate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学生信息档案：以学工号（socialUid）为主键，在每次 CAS/OIDC 登录交换时 upsert。
 * <p>字段值来自认证中心返回的属性，映射键见 {@link StudentMapping}；
 * rawAttributes 保留原始属性 JSON，便于管理员核对字段名后调整映射。</p>
 */
public final class StudentProfile {

    private final String socialUid;
    private final String name;
    private final String dept;
    private final String major;
    private final String grade;
    private final String className;
    private final String email;
    private final String phone;
    private final String protocol;
    private final String rawAttributes;
    private final long firstSeenAt;
    private final long lastSeenAt;
    private final long loginCount;

    public StudentProfile(
            String socialUid,
            String name,
            String dept,
            String major,
            String grade,
            String className,
            String email,
            String phone,
            String protocol,
            String rawAttributes,
            long firstSeenAt,
            long lastSeenAt,
            long loginCount
    ) {
        this.socialUid = socialUid;
        this.name = name;
        this.dept = dept;
        this.major = major;
        this.grade = grade;
        this.className = className;
        this.email = email;
        this.phone = phone;
        this.protocol = protocol;
        this.rawAttributes = rawAttributes;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.loginCount = loginCount;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("socialUid", socialUid);
        document.put("name", name);
        document.put("dept", dept);
        document.put("major", major);
        document.put("grade", grade);
        document.put("className", className);
        document.put("email", email);
        document.put("phone", phone);
        document.put("protocol", protocol);
        document.put("rawAttributes", rawAttributes);
        document.put("firstSeenAt", firstSeenAt);
        document.put("lastSeenAt", lastSeenAt);
        document.put("loginCount", loginCount);
        return document;
    }

    public String socialUid() {
        return socialUid;
    }

    public String name() {
        return name;
    }

    public String dept() {
        return dept;
    }

    public String major() {
        return major;
    }

    public String grade() {
        return grade;
    }

    public String className() {
        return className;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String protocol() {
        return protocol;
    }

    public String rawAttributes() {
        return rawAttributes;
    }

    public long firstSeenAt() {
        return firstSeenAt;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    public long loginCount() {
        return loginCount;
    }
}
