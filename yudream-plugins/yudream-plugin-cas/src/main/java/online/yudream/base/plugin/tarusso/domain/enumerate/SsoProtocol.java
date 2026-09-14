package online.yudream.base.plugin.tarusso.domain.enumerate;

public enum SsoProtocol {
    CAS,
    OIDC;

    public static SsoProtocol from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CAS;
        }
        try {
            return SsoProtocol.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return CAS;
        }
    }

    public String typeCode() {
        return name().toLowerCase();
    }
}
