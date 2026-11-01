package com.diybracelet.mall.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 有界扫描到期订单；逐单独立事务，重复或并发执行由订单行锁保护。 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.orders.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryScheduler.class);
    private final OrderService orders;

    public OrderExpiryScheduler(OrderService orders) { this.orders = orders; }

    @Scheduled(fixedDelayString = "${app.orders.expiry-scan-delay:PT30S}",
            initialDelayString = "${app.orders.expiry-scan-delay:PT30S}")
    public void closeExpiredOrders() {
        for (Long orderId : orders.expiredOrderIds()) {
            try { orders.closeExpired(orderId); }
            catch (RuntimeException failure) {
                log.error("Failed to expire order {}; next scan will retry", orderId, failure);
            }
        }
    }
}
