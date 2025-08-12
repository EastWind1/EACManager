package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.MappingTarget;

/**
 * 基础实体、DTO 映射
 */
public interface BaseMapper<E, DTO> {
    /**
     * 转换为实体
     *
     * @param dto DTO
     * @return 实体
     */
    E toEntity(DTO dto);

    /**
     * 转换为 DTO
     *
     * @param entity 实体
     * @return DTO
     */
    DTO toDTO(E entity);

    /**
     * 转换为基础 DTO，用于列表展示
     *
     * @param entity 实体
     * @return 基础 DTO
     */
    DTO toBaseDTO(E entity);

    /**
     * 更新实体
     */
    void updateEntityFromDTO(DTO dto, @MappingTarget E entity);
}
