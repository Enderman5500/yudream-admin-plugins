package online.yudream.base.plugin.invite.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import online.yudream.base.plugin.invite.domain.InviteBinding;
import online.yudream.base.plugin.invite.infrastructure.FakeDocumentStore;
import online.yudream.base.plugin.invite.infrastructure.InviteBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 邀请码绑定规则测试：一码一用户、一用户多码、数量不限；用户不可自行解绑，仅管理员可删除。 */
class InviteBindingServiceTest {

    private InviteBindingService service;
    private InviteBindingRepository bindings;

    @BeforeEach
    void setUp() {
        FakeDocumentStore store = new FakeDocumentStore();
        bindings = new InviteBindingRepository(store);
        service = new InviteBindingService(bindings);
    }

    @Test
    void bindAndListMine() {
        service.bindMine("1001", "  MUACODE-001  ", "外校同学A");
        service.bindMine("1001", "MUACODE-002", "");

        PageResult<InviteBinding> mine = service.listMine("1001", 1, 20);
        assertEquals(2, mine.total());
        // 绑定码去空白
        assertTrue(mine.records().stream().anyMatch(binding -> "MUACODE-001".equals(binding.code())));
    }

    @Test
    void sameCodeRejectedForOtherUser() {
        service.bindMine("1001", "MUACODE-001", null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.bindMine("1002", "MUACODE-001", null));
        assertTrue(e.getMessage().contains("已被其他用户绑定"));
    }

    @Test
    void duplicateBindBySameUserRejected() {
        service.bindMine("1001", "MUACODE-001", null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.bindMine("1001", "MUACODE-001", null));
        assertTrue(e.getMessage().contains("已经绑定过"));
    }

    @Test
    void unlimitedBindingsPerUser() {
        for (int i = 1; i <= 15; i++) {
            service.bindMine("1001", "CODE-" + i, null);
        }
        assertEquals(15, service.listMine("1001", 1, 20).total());
    }

    @Test
    void blankCodeRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.bindMine("1001", "   ", null));
        assertThrows(IllegalArgumentException.class, () -> service.bindMine("1001", null, null));
    }

    @Test
    void adminDeleteOnlyRemovalPathAndCodeRebindable() {
        // 用户端无解绑用例；删除绑定记录只能走管理端 adminDelete，
        // 删除后邀请码恢复可用，可被其他用户重新绑定。
        InviteBinding binding = service.bindMine("1001", "MUACODE-001", null);
        service.adminDelete(binding.id());
        assertEquals(0, service.listMine("1001", 1, 20).total());

        service.bindMine("1002", "MUACODE-001", null);
        assertEquals(1, service.listMine("1002", 1, 20).total());
    }

    @Test
    void adminPageFiltersAndDelete() {
        service.bindMine("1001", "MUACODE-001", "同学A");
        service.bindMine("1002", "XX-002", "同学B");

        assertEquals(2, service.adminPage("", 1, 10).total());
        assertEquals(1, service.adminPage("MUACODE", 1, 10).total());
        assertEquals(1, service.adminPage("1002", 1, 10).total());

        List<InviteBinding> records = service.adminPage("XX-002", 1, 10).records();
        service.adminDelete(records.get(0).id());
        assertEquals(1, service.adminPage("", 1, 10).total());
        assertThrows(NotFoundException.class, () -> service.adminDelete(records.get(0).id()));
    }
}
