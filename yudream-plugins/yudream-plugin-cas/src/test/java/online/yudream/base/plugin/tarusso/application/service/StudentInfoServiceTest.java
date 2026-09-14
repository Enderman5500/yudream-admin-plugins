package online.yudream.base.plugin.tarusso.application.service;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.tarusso.domain.aggregate.StudentMapping;
import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;
import online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.repository.StudentMappingDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.repository.StudentProfileDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentInfoServiceTest {

    @Test
    void recordAutoDetectsCommonKeys() {
        PagedDocuments documents = new PagedDocuments();
        StudentInfoService service = service(documents);
        service.record(identity("20230101", "张三", Map.of(
                "cn", "张三",
                "department", "信息工程学院",
                "major", "计算机科学与技术",
                "studentGrade", "2023",
                "className", "计算机23-1班",
                "mail", "zhang@taru.edu.cn",
                "mobile", "13900000000"
        )), SsoProtocol.CAS);

        Optional<Map<String, Object>> detail = service.detail("20230101");
        assertTrue(detail.isPresent());
        assertEquals("张三", detail.get().get("name"));
        assertEquals("信息工程学院", detail.get().get("dept"));
        assertEquals("计算机科学与技术", detail.get().get("major"));
        assertEquals("2023", detail.get().get("grade"));
        assertEquals("计算机23-1班", detail.get().get("className"));
        assertEquals("zhang@taru.edu.cn", detail.get().get("email"));
        assertEquals("13900000000", detail.get().get("phone"));
        assertEquals("CAS", detail.get().get("protocol"));
        assertEquals(1L, detail.get().get("loginCount"));
        assertTrue(String.valueOf(detail.get().get("rawAttributes")).contains("department"));
    }

    @Test
    void recordHonorsExplicitKeys() {
        PagedDocuments documents = new PagedDocuments();
        StudentInfoService service = service(documents);
        service.saveMapping(new StudentMapping(false, "xm", "xy", "zy", "nj", "bj"));
        service.record(identity("20230102", "李四", Map.of(
                "xm", "李四",
                "xy", "水利与土木工程学院",
                "zy", "农业水利工程",
                "nj", "2022",
                "bj", "农水22-2班"
        )), SsoProtocol.OIDC);

        Map<String, Object> detail = service.detail("20230102").orElseThrow();
        assertEquals("李四", detail.get("name"));
        assertEquals("水利与土木工程学院", detail.get("dept"));
        assertEquals("农业水利工程", detail.get("major"));
        assertEquals("2022", detail.get("grade"));
        assertEquals("农水22-2班", detail.get("className"));
        assertEquals("OIDC", detail.get("protocol"));
    }

    @Test
    void recordUpsertsAndKeepsFirstSeen() {
        PagedDocuments documents = new PagedDocuments();
        StudentInfoService service = service(documents);
        service.record(identity("20230103", "王五", Map.of("cn", "王五")), SsoProtocol.CAS);
        long firstSeen = (Long) service.detail("20230103").orElseThrow().get("firstSeenAt");
        service.record(identity("20230103", "王五", Map.of("cn", "王五", "department", "机械工程学院")), SsoProtocol.CAS);
        Map<String, Object> detail = service.detail("20230103").orElseThrow();
        assertEquals(2L, detail.get("loginCount"));
        assertEquals(firstSeen, detail.get("firstSeenAt"));
        assertEquals("机械工程学院", detail.get("dept"));
    }

    @Test
    void pageAndSearch() {
        PagedDocuments documents = new PagedDocuments();
        StudentInfoService service = service(documents);
        service.record(identity("20230104", "赵六", Map.of("cn", "赵六", "department", "信息工程学院")), SsoProtocol.CAS);
        service.record(identity("20230105", "钱七", Map.of("cn", "钱七", "department", "经济与管理学院")), SsoProtocol.CAS);

        Map<String, Object> all = service.page(1, 20, null);
        assertEquals(2L, all.get("total"));

        Map<String, Object> hit = service.page(1, 20, "信息工程");
        assertEquals(1L, hit.get("total"));
        assertEquals("赵六", ((List<Map<String, Object>>) hit.get("items")).get(0).get("name"));

        Map<String, Object> miss = service.page(1, 20, "不存在的人");
        assertEquals(0L, miss.get("total"));
    }

    @Test
    void mappingPersists() {
        PagedDocuments documents = new PagedDocuments();
        StudentInfoService service = service(documents);
        assertEquals(false, service.mapping().requireBinding());
        StudentMapping saved = service.saveMapping(new StudentMapping(true, "cn", "", "", "", "clazz"));
        assertTrue(saved.requireBinding());
        assertEquals("cn", service.mapping().nameKey());
        assertEquals("clazz", service.mapping().classKey());
        assertTrue(service.mapping().requireBinding());
    }

    @Test
    void prefillReturnsMinimalFieldsOnly() {
        PagedDocuments documents = new PagedDocuments();
        StudentInfoService service = service(documents);
        service.record(identity("20230106", "孙八", Map.of(
                "cn", "孙八",
                "department", "经济与管理学院",
                "mail", "sun@taru.edu.cn",
                "mobile", "13800000000"
        )), SsoProtocol.CAS);

        Map<String, Object> prefill = service.prefill("20230106").orElseThrow();
        assertEquals("20230106", prefill.get("studentNo"));
        assertEquals("孙八", prefill.get("studentName"));
        assertEquals("经济与管理学院", prefill.get("college"));
        assertTrue(prefill.containsKey("className"));
        // 最小字段集：不包含邮箱、电话、原始属性
        assertTrue(!prefill.containsKey("email"));
        assertTrue(!prefill.containsKey("phone"));
        assertTrue(!prefill.containsKey("rawAttributes"));
        assertTrue(service.prefill("不存在的学号").isEmpty());
        assertTrue(service.prefill(null).isEmpty());
    }

    private static StudentInfoService service(PagedDocuments documents) {
        return new StudentInfoService(
                new StudentMappingDocumentRepository(documents),
                new StudentProfileDocumentRepository(documents)
        );
    }

    private static SsoProtocolClient.ExternalIdentity identity(String uid, String nickname, Map<String, String> attributes) {
        return new SsoProtocolClient.ExternalIdentity(uid, nickname, "", "", "", attributes);
    }

    /** 支持分页 findAll 的内存文档桩。 */
    private static final class PagedDocuments implements PluginDocumentStore {
        private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

        @Override
        public Map<String, Object> save(String collection, String id, Map<String, Object> document) {
            Map<String, Object> copy = new LinkedHashMap<>(document);
            store.put(collection + "/" + id, copy);
            return copy;
        }

        @Override
        public Optional<Map<String, Object>> findById(String collection, String id) {
            Map<String, Object> document = store.get(collection + "/" + id);
            return document == null ? Optional.empty() : Optional.of(new LinkedHashMap<>(document));
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> findAll(String collection, int page, int size) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : store.entrySet()) {
                if (entry.getKey().startsWith(collection + "/")) {
                    items.add(new LinkedHashMap<>(entry.getValue()));
                }
            }
            items.sort(Comparator.comparing(d -> String.valueOf(d.get("socialUid"))));
            int from = Math.min((Math.max(page, 1) - 1) * Math.max(size, 1), items.size());
            int to = Math.min(from + Math.max(size, 1), items.size());
            return items.subList(from, to);
        }

        @Override
        public List<Map<String, Object>> findByField(String collection, String field, Object value, int page, int size) {
            return List.of();
        }

        @Override
        public long count(String collection) {
            return store.keySet().stream().filter(k -> k.startsWith(collection + "/")).count();
        }

        @Override
        public void delete(String collection, String id) {
            store.remove(collection + "/" + id);
        }
    }
}
