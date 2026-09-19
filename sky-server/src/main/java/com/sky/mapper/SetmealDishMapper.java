package com.sky.mapper;

import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealDishMapper {


    /**
     * 根据菜品id查询对应的套餐id
     *
     * @param dishIds
     *
     */

    List<Long> getSetmealIdsByDishIds(@Param("dishIds") List<Long> dishIds);

    /**
     * 新增菜品id鱼对应套餐id对应
     */
    void insertBatch(List<SetmealDish> setmealDishes);

    // 根据套餐id查菜品
    @Select("select * from setmeal_dish where setmeal_id = #{setmealDishId}")
    List<SetmealDish> getBySetmealDishid(Long setmealDishId);

    // 根据套餐id删除记录
    @Delete("delete from setmeal_dish where setmeal_id = #{SetmealDishId}")
    void deleteBySetmealDishId(Long SetmealDishId);

    /**
     * 批量删除套餐对应菜品列表
     *
     * @param SetmealDishIds
     */
    void batchDeleteBySetmealDishId(List<Long> SetmealDishIds);

}
