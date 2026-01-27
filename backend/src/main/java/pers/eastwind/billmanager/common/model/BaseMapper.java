package pers.eastwind.billmanager.common.model;

import org.mapstruct.MappingTarget;
import pers.eastwind.billmanager.common.exception.BizException;
import pers.eastwind.billmanager.user.model.AuthorityRole;
import pers.eastwind.billmanager.user.util.AuthUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
     * 转换为基础 DTO 列表
     */
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

    /**
     * 更新实体
     */
    void updateEntityFromDTO(DTO dto, @MappingTarget E entity);
    /**
     * 权限判断
     * @param roles 角色
     * @see AuthorityRole
     */
    default boolean hasRole(AuthorityRole... roles) {
        return AuthUtil.hasAnyRole(roles);
    }
    /**
     * 权限判断
     * @param roles 角色名称，对应 {@link AuthorityRole} 的枚举名称，不含 ROLE_ 前缀
     * @see AuthorityRole
     */
    default boolean hasRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return true;
        }
        String rolePrefix = "ROLE_";
        AuthorityRole[] roleEnums = new AuthorityRole[roles.length];
        for (int i = 0; i < roles.length; i++) {
            try {
                roleEnums[i] = AuthorityRole.valueOf(rolePrefix + roles[i]);
            } catch (IllegalArgumentException e) {
                throw new BizException("权限角色不存在: " + roles[i]);
            }
        }
        return hasRole(roleEnums);
    }
}
