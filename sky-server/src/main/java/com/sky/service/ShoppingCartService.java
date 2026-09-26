package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 添加购物车操作
     *
     * @param shoppingCartDTO
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);


    /**
     * 查询购物车列表
     * @return
     */
    List<ShoppingCart> list();


    /**
     * 清空购物车
     */
    void clearShoppingCart();


    /**
     * 清除购物车单个菜品
     * @param shoppingCartDTO
     */
    void clearShoppingCartSingle(ShoppingCartDTO shoppingCartDTO);
}
