package online.yudream.base.plugin.tarusso.infrastructure.repository;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.tarusso.domain.aggregate.StudentMapping;
import online.yudream.base.plugin.tarusso.domain.repo.StudentMappingRepository;
import online.yudream.base.plugin.tarusso.infrastructure.support.DocValues;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StudentMappingDocumentRepository implements StudentMappingRepository {

    static final String COLLECTION = "student-mapping";
    static final String ID = "global";

    private final PluginDocumentStore documents;

    public StudentMappingDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public StudentMapping get() {
        return documents.findById(COLLECTION, ID).map(this::toMapping).orElseGet(StudentMapping::defaults);
    }

    @Override
    public StudentMapping save(StudentMapping mapping) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("requireBinding", mapping.requireBinding());
        document.put("nameKey", mapping.nameKey());
        document.put("deptKey", mapping.deptKey());
        document.put("majorKey", mapping.majorKey());
        document.put("gradeKey", mapping.gradeKey());
        document.put("classKey", mapping.classKey());
        return toMapping(documents.save(COLLECTION, ID, DocValues.stripNulls(document)));
    }

    private StudentMapping toMapping(Map<String, Object> document) {
        return new StudentMapping(
                DocValues.bool(document, "requireBinding", false),
                orEmpty(DocValues.string(document, "nameKey")),
                orEmpty(DocValues.string(document, "deptKey")),
                orEmpty(DocValues.string(document, "majorKey")),
                orEmpty(DocValues.string(document, "gradeKey")),
                orEmpty(DocValues.string(document, "classKey"))
        );
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
