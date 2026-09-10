package com.sky.service;

import com.sky.dto.DishDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

public interface DishService {
    /**
     * 保存菜品
     *
     * @param dishDTO
     */
    public void saveWithFlavor(DishDTO dishDTO);
}
