package pers.eastwind.billmanager.common.model;

import org.mapstruct.MappingTarget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface BaseMapper<E, DTO> {
    E toEntity(DTO dto);

    DTO toDTO(E entity);

    DTO toBaseDTO(E entity);

    default List<DTO> toBaseDTOs(Collection<E> entities) {
        List<DTO> res = new ArrayList<>();
        if (entities == null) {
            return res;
        }
        for (E entity : entities) {
            res.add(toBaseDTO(entity));
        }
        return res;
    }

    void updateEntityFromDTO(DTO dto, @MappingTarget E entity);
}
