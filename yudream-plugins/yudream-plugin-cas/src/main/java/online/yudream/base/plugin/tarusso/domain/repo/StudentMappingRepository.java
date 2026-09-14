package online.yudream.base.plugin.tarusso.domain.repo;

import online.yudream.base.plugin.tarusso.domain.aggregate.StudentMapping;

public interface StudentMappingRepository {

    StudentMapping get();

    StudentMapping save(StudentMapping mapping);
}
