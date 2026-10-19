package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();
        // 购物车校验和地址校验
        //1. 处理各种业务异常（地址簿为空、购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new ShoppingCartBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 订单表填入数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());
        orderMapper.insert(orders);
        // 订单明细表填入数据
        ShoppingCart shoppingCart = ShoppingCart.builder().userId(userId).build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart shoppingCartItem : shoppingCartList) {
            // 填入订单id做关联
            OrderDetail orderDetail = OrderDetail.builder().orderId(orders.getId()).build();
            BeanUtils.copyProperties(shoppingCartItem, orderDetail);
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insetyBatch(orderDetailList);

        // 清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(LocalDateTime.now())
                .build();


        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }


    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type",1); // 1表示来单提醒 2表示客户催单
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号：" + outTradeNo);
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 历史订单查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orders.getId()));
                orderVOList.add(orderVO);
            }
        }

        long total = page == null ? 0 : page.getTotal();
        return new PageResult(total, orderVOList);
    }

    @Override
    public OrderVO orderDetail(Long id) {
        Orders orders = orderMapper.getByIdAndUserId(id, BaseContext.getCurrentId());
        OrderVO orderVO = new OrderVO();
        if (orders != null) {
            BeanUtils.copyProperties(orders, orderVO);
            orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orders.getId()));
        } else {
            throw new OrderBusinessException("订单不存在");
        }

        return orderVO;
    }

    /**
     * 订单取消
     *
     * @param id
     * @throws Exception
     */
    @Override
    public void cancel(Long id) throws Exception {
        Orders orderInfo = orderMapper.getByIdAndUserId(id, BaseContext.getCurrentId());
        // 校验订单是否存在
        if (orderInfo == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Integer status = orderInfo.getStatus();
        if (status > 2) {
            throw new OrderBusinessException("需电话沟通商家");
        }

        Orders updateOrders = Orders.builder().id(orderInfo.getId()).status(Orders.CANCELLED).build();

        // 订单处于待接单状态下取消，需要进行退款
//        这个在就会报错，真实场景需要退款
//        if (status.equals(Orders.TO_BE_CONFIRMED)) {
//            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    orderInfo.getNumber(), //商户订单号
//                    orderInfo.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额
//
//            //支付状态修改为 退款
//        }
        updateOrders.setPayStatus(Orders.REFUND);
        // 更新订单状态、取消原因、取消时间
        updateOrders.setCancelReason("用户取消");
        updateOrders.setCancelTime(LocalDateTime.now());
        orderMapper.update(updateOrders);

    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Override
    public void repetition(Long id) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        List<ShoppingCart> shoppingCartList = new ArrayList<>();

        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCartList.add(shoppingCart);
        }

        shoppingCartMapper.insertBatch(shoppingCartList);

    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                String orderDishesStr = getOrderDishesStr(orderDetailList);
                orderVO.setOrderDishes(orderDishesStr);
                orderVO.setOrderDetailList(orderDetailList);
                orderVOList.add(orderVO);
            }
        }

        long total = page == null ? 0 : page.getTotal();
        return new PageResult(total, orderVOList);

    }

    /**
     * 根据订单列表获取菜品信息字符串
     *
     * @param orderDetailList
     * @return
     */
    private String getOrderDishesStr(List<OrderDetail> orderDetailList) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
//        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    @Override
    public OrderStatisticsVO orderStatistics() {

//        public static final Integer TO_BE_CONFIRMED = 2;
//        public static final Integer CONFIRMED = 3;
//        public static final Integer DELIVERY_IN_PROGRESS = 4;

//        自写的方法1
//        Integer toBeConfirmed = Optional.ofNullable(orderMapper.getByStatus(Orders.TO_BE_CONFIRMED)).map(List::size).orElse(0);
//        Integer confirmed = Optional.ofNullable(orderMapper.getByStatus(Orders.CONFIRMED)).map(List::size).orElse(0);
//        Integer deliveryInProgress = Optional.ofNullable(orderMapper.getByStatus(Orders.DELIVERY_IN_PROGRESS)).map(List::size).orElse(0);
//        return OrderStatisticsVO.builder()
//                .toBeConfirmed(toBeConfirmed)
//                .confirmed(confirmed)
//                .deliveryInProgress(deliveryInProgress)
//                .build();

        // 老师的方法2参考答案
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        return OrderStatisticsVO.builder()
                .toBeConfirmed(toBeConfirmed)
                .confirmed(confirmed)
                .deliveryInProgress(deliveryInProgress)
                .build();
    }

    /**
     * 订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder().id(ordersConfirmDTO.getId()).status(Orders.CONFIRMED).build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param OrdersRejectionDTO
     * @throws Exception
     */
    @Override
    public void rejection(OrdersRejectionDTO OrdersRejectionDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(OrdersRejectionDTO.getId());

        // 订单只有存在且状态为2（待接单）才可以拒单
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);
        }

        // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setRejectionReason(OrdersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        orders.setPayStatus(Orders.REFUND);

        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     * @throws Exception
     */
    @Override
    public void cancelOrders(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());

        //支付状态
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            //用户已支付，需要退款
            String refund = weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    new BigDecimal(0.01),
                    new BigDecimal(0.01));
            log.info("申请退款：{}", refund);

        }

        // 拒单需要退款，根据订单id更新订单状态、拒单原因、取消时间
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setPayStatus(Orders.REFUND);
//        orders.setRejectionReason(OrdersRejectionDTO.getRejectionReason());
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);

    }

    /**
     * 更新状态为派送
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

//        if (Orders.CONFIRMED.equals(ordersDB.getStatus())) {
//            ordersDB.setStatus(Orders.DELIVERY_IN_PROGRESS);
//        }
//        else {
//            throw new OrderBusinessException("只有状态为“待派送”的订单可以执行派送订单操作");
//        }
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !Orders.DELIVERY_IN_PROGRESS.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 客户催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("type",2); //1表示来单提醒 2表示客户催单
        map.put("orderId",id);
        map.put("content","订单号：" + ordersDB.getNumber());
        String jsonString = JSON.toJSONString(map);
        // 通过websocket向客户端浏览器推送消息
        webSocketServer.sendToAllClient(jsonString);
    }
}
