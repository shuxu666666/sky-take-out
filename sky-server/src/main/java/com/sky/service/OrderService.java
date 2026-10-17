package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {
    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);


    PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    OrderVO orderDetail(Long id);

    void cancel(Long id) throws Exception;

    void repetition(Long id);

    /**
     * 订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO orderStatistics();

    OrderVO details(Long id);

    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    void rejection(OrdersRejectionDTO ordersConfirmDTO) throws Exception;

    /**
     * 商家端取消订单
     *
     * @param ordersCancelDTO
     */
    void cancelOrders(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 订单派送
     *
     * @param id
     */
    void delivery(Long id);

    /**
     * 订单完成
     *
     * @param id
     */
    void complete(Long id);
}
