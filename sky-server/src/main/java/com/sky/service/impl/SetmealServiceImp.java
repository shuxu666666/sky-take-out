package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SetmealServiceImp implements SetmealService {
    @Autowired
    SetmealMapper setmealMapper;

    @Autowired
    SetmealDishMapper setmealDishMapper;

    @Autowired
    DishMapper dishMapper;

    /**
     * 新增菜品
     *
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void insert(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        setmeal.setStatus(0);
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 插入套餐数据
        setmealMapper.insert(setmeal);

        // 插入套餐数据与菜品对应数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();

        if (setmealDishes != null && setmealDishes.size() > 0) {
            setmealDishes.forEach(item -> item.setSetmealId(setmeal.getId()));
        }

        setmealDishMapper.insertBatch(setmealDishes);

    }

    /**
     * 套餐列表查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());

        Page<SetmealVO> setmealList = setmealMapper.pageQuery(setmealPageQueryDTO);


        return new PageResult(setmealList.getTotal(), setmealList.getResult());
    }

    /**
     * 根据id查询套餐
     *
     * @param id
     * @return
     */

    @Override
    public SetmealVO getById(Long id) {
        SetmealVO setmealVO = new SetmealVO();
        Setmeal setmeal = setmealMapper.getById(id);
        BeanUtils.copyProperties(setmeal, setmealVO);
        // 查询所关联的菜品
        List<SetmealDish> setmealDishList = setmealDishMapper.getBySetmealDishid(id);
        setmealVO.setSetmealDishes(setmealDishList);
        return setmealVO;
    }

    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);

        List<SetmealDish> setmealDish = setmealDTO.getSetmealDishes();
        if (setmealDish != null && setmealDish.size() > 0) {
            setmealDish.forEach(item -> item.setSetmealId(setmealDTO.getId()));
            setmealDishMapper.deleteBySetmealDishId(setmealDTO.getId());
            setmealDishMapper.insertBatch(setmealDish);
        }
    }

    /**
     * 批量删除套餐与对应菜品
     *
     * @param ids
     */
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        setmealMapper.batchDelete(ids);
        setmealDishMapper.batchDeleteBySetmealDishId(ids);

    }


    /**
     * 更新套餐售卖状态
     *
     * @param status
     * @param id
     * @return
     */
    @Override
    public Boolean updateStatus(Integer status, Long id) {
        List<SetmealDish> setmealDishList = setmealDishMapper.getBySetmealDishid(id);
        // 获取菜单id列表
//        List<Long> dishIds = setmealDishList.stream().map(SetmealDish::getDishId).collect(Collectors.toList());
        List<Long> dishIds = setmealDishList.stream().map((item) -> item.getDishId()).collect(Collectors.toList());
        for (Long dishId : dishIds) {
            Dish dish = dishMapper.getById(dishId);
//            log.info("id{}:dish数据{},", dishId, dish);
            if (dish.getStatus() == 0) {
                return false;
            }
        }
        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.update(setmeal);
        return true;
    }
}
