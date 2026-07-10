package com.diybracelet.mall.inventory.application;

import com.diybracelet.mall.inventory.domain.InventoryBalance;
import com.diybracelet.mall.inventory.domain.InventoryLedger;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryLedgerMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 库存预占服务。
 *
 * <p>下单阶段只执行预占，支付成功后才转为已售。这样用户放弃支付时可以安全释放库存，
 * 后续接入真实支付也能沿用同一状态流转。</p>
 */
@Service
public class InventoryReservationService {

    private final InventoryBalanceMapper balanceMapper;
    private final InventoryLedgerMapper ledgerMapper;

    public InventoryReservationService(
            InventoryBalanceMapper balanceMapper,
            InventoryLedgerMapper ledgerMapper
    ) {
        this.balanceMapper = balanceMapper;
        this.ledgerMapper = ledgerMapper;
    }

    /**
     * 为订单预占多个 SKU 库存。
     *
     * @param orderId 订单主键
     * @param quantities SKU 到数量的映射
     */
    public void reserve(Long orderId, Map<Long, Integer> quantities) {
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            reserveOne(orderId, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 确认订单销售，把所有 SKU 的预占库存转为已售库存。
     *
     * @param orderId 订单主键
     * @param quantities SKU 到数量的映射
     */
    public void confirmSale(Long orderId, Map<Long, Integer> quantities) {
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            transferReserved(orderId, entry.getKey(), entry.getValue(), true);
        }
    }

    /**
     * 释放订单预占，把所有 SKU 的预占库存退回可用库存。
     *
     * @param orderId 订单主键
     * @param quantities SKU 到数量的映射
     */
    public void release(Long orderId, Map<Long, Integer> quantities) {
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            transferReserved(orderId, entry.getKey(), entry.getValue(), false);
        }
    }

    private void transferReserved(
            Long orderId,
            Long skuId,
            Integer quantity,
            boolean confirmSale
    ) {
        InventoryBalance before = balanceMapper.selectBySkuForUpdate(skuId);
        if (before == null || before.getReservedQty() < quantity) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "订单预占库存状态异常"
            );
        }
        int updated = confirmSale
                ? balanceMapper.confirmSale(skuId, quantity)
                : balanceMapper.release(skuId, quantity);
        if (updated != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "订单预占库存状态异常"
            );
        }

        InventoryLedger ledger = new InventoryLedger();
        ledger.setBeadSkuId(skuId);
        ledger.setOrderId(orderId);
        ledger.setChangeType(confirmSale ? "PAY_CONFIRM" : "RELEASE");
        ledger.setQuantity(quantity);
        ledger.setBeforeAvailableQty(before.getAvailableQty());
        ledger.setAfterAvailableQty(confirmSale
                ? before.getAvailableQty()
                : before.getAvailableQty() + quantity);
        ledger.setBeforeReservedQty(before.getReservedQty());
        ledger.setAfterReservedQty(before.getReservedQty() - quantity);
        ledger.setBeforeSoldQty(before.getSoldQty());
        ledger.setAfterSoldQty(confirmSale
                ? before.getSoldQty() + quantity
                : before.getSoldQty());
        ledger.setNote(confirmSale
                ? "模拟支付成功确认销售"
                : "待支付订单取消释放库存");
        ledgerMapper.insert(ledger);
    }

    private void reserveOne(Long orderId, Long skuId, Integer quantity) {
        InventoryBalance before = balanceMapper.selectBySkuForUpdate(skuId);
        if (before == null || before.getAvailableQty() < quantity) {
            throw new BusinessException(
                    ErrorCode.INVENTORY_INSUFFICIENT,
                    "库存不足，无法创建订单"
            );
        }
        int updated = balanceMapper.reserve(skuId, quantity);
        if (updated != 1) {
            throw new BusinessException(
                    ErrorCode.INVENTORY_INSUFFICIENT,
                    "库存不足，无法创建订单"
            );
        }
        InventoryLedger ledger = new InventoryLedger();
        ledger.setBeadSkuId(skuId);
        ledger.setOrderId(orderId);
        ledger.setChangeType("RESERVE");
        ledger.setQuantity(quantity);
        ledger.setBeforeAvailableQty(before.getAvailableQty());
        ledger.setAfterAvailableQty(before.getAvailableQty() - quantity);
        ledger.setBeforeReservedQty(before.getReservedQty());
        ledger.setAfterReservedQty(before.getReservedQty() + quantity);
        ledger.setBeforeSoldQty(before.getSoldQty());
        ledger.setAfterSoldQty(before.getSoldQty());
        ledger.setNote("订单创建预占库存");
        ledgerMapper.insert(ledger);
    }
}
